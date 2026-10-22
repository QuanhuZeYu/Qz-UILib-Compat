import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

// 只替换 gh 远端；完整执行生产脚本，检查创建/重跑/失败时的实际命令顺序。
function fixture(t, { existing = false, viewFailure = false, uploadFailure = false } = {}) {
    const directory = mkdtempSync(join(tmpdir(), 'compat-release-test-'));
    t.after(() => rmSync(directory, { recursive: true, force: true }));
    const trace = join(directory, 'calls.jsonl');
    const fake = join(directory, 'gh.mjs');
    const state = join(directory, 'exists');
    if (existing) writeFileSync(state, 'yes');
    writeFileSync(fake, String.raw`import { appendFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
const args = process.argv.slice(2);
const entry = { args };
const notes = args.indexOf('--notes-file');
if (notes >= 0) entry.body = readFileSync(args[notes + 1], 'utf8');
appendFileSync(process.env.TEST_TRACE, JSON.stringify(entry) + '\n');
if (args[0] === 'auth') process.exit(0);
if (args[1] === 'view' && process.env.TEST_VIEW_FAILURE === 'true') { console.error('HTTP 500'); process.exit(1); }
if (args[1] === 'view' && !existsSync(process.env.TEST_STATE)) { console.error('release not found'); process.exit(1); }
if (args[1] === 'create') writeFileSync(process.env.TEST_STATE, 'yes');
if (args[1] === 'upload' && process.env.TEST_UPLOAD_FAILURE === 'true') { console.error('upload failed'); process.exit(1); }
`);
    const asset = join(directory, 'compat.jar');
    const changelog = join(directory, 'changelog.md');
    writeFileSync(asset, 'artifact');
    writeFileSync(changelog, '# 静态发布说明\n');
    return {
        run(tag = '1.0.0-beta1', version = '1.0.0-beta1') {
            return spawnSync(process.execPath, [resolve('tools/release.mjs'),
                '--repo', 'example/compat', '--tag', tag, '--version', version,
                '--title', 'Compat ' + version, '--asset', asset, '--changelog', changelog], {
                encoding: 'utf8', env: { ...process.env, QZ_GH_BIN: JSON.stringify([process.execPath, fake]),
                    TEST_TRACE: trace, TEST_STATE: state, TEST_VIEW_FAILURE: String(viewFailure), TEST_UPLOAD_FAILURE: String(uploadFailure) }
            });
        },
        calls() { return readFileSync(trace, 'utf8').trim().split('\n').map(line => JSON.parse(line)); }
    };
}

test('beta 先创建草稿、上传后公开，创建和重跑正文只含静态说明', t => {
    const f = fixture(t);
    const first = f.run();
    assert.equal(first.status, 0, first.stderr);
    let calls = f.calls();
    assert.deepEqual(calls.map(x => x.args.slice(0, 2)), [['auth', 'status'], ['release', 'view'], ['release', 'create'], ['release', 'upload'], ['release', 'edit']]);
    assert.ok(calls[2].args.includes('--verify-tag'));
    assert.ok(calls[2].args.includes('--draft'));
    assert.ok(calls[2].args.includes('--prerelease'));
    assert.ok(calls[3].args.includes('--clobber'));
    assert.ok(calls[4].args.includes('--prerelease=true'));
    assert.ok(calls[4].args.includes('--latest=false'));
    assert.ok(calls[4].args.includes('--draft=false'));
    assert.equal(f.run().status, 0);
    calls = f.calls();
    assert.equal(calls.filter(x => x.args[1] === 'create').length, 1);
    for (const call of calls.filter(x => x.body !== undefined)) {
        assert.equal(call.body, '# 静态发布说明\n');
    }
});

test('稳定 tag 可设为 latest', t => {
    const f = fixture(t, { existing: true });
    assert.equal(f.run('v1.0.0', '1.0.0').status, 0);
    const edit = f.calls().find(x => x.args[1] === 'edit');
    assert.ok(edit.args.includes('--prerelease=false'));
    assert.ok(edit.args.includes('--latest=true'));
    assert.equal(edit.body, '# 静态发布说明\n');
});

test('tag 格式和版本不一致在接触远端前失败', t => {
    const f = fixture(t);
    assert.notEqual(f.run('1.0.0-beta1', '1.0.0-beta2').status, 0);
    assert.notEqual(f.run('bad-tag').status, 0);
});

test('查询非 404 失败不会创建或上传', t => {
    const f = fixture(t, { viewFailure: true });
    assert.notEqual(f.run().status, 0);
    assert.deepEqual(f.calls().map(x => x.args[1]), ['status', 'view']);
});

test('上传失败不得公开草稿或宣布发布成功', t => {
    const f = fixture(t, { uploadFailure: true });
    const result = f.run();
    assert.notEqual(result.status, 0);
    assert.equal(f.calls().some(x => x.args[1] === 'edit'), false);
    assert.doesNotMatch(result.stdout, /已公开 Release/);
});
