#!/usr/bin/env node
/**
 * 用 gh 创建或更新同一 tag 的 GitHub Release。
 * 新 Release 先以草稿创建、上传资产后再公开；已有 Release 只替换同名资产并更新属性，保留其他资产。
 */
import { existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, join } from 'node:path';
import { spawnSync } from 'node:child_process';

function fail(message) {
    console.error('发布失败: ' + message);
    process.exit(1);
}

function options(name) {
    const values = [];
    for (let index = 2; index < process.argv.length; index++) {
        if (process.argv[index] !== '--' + name) continue;
        if (index + 1 >= process.argv.length || process.argv[index + 1].startsWith('--')) fail('缺少 --' + name + ' 的值');
        values.push(process.argv[index + 1]);
    }
    if (!values.length) fail('缺少 --' + name);
    return values;
}

function option(name) {
    return options(name)[0];
}

// 本地验证可用 QZ_GH_BIN 指向替代 gh 命令（JSON 数组）；生产默认使用 gh。
const ghCommand = process.env.QZ_GH_BIN ? JSON.parse(process.env.QZ_GH_BIN) : ['gh'];
function gh(args) {
    const result = spawnSync(ghCommand[0], [...ghCommand.slice(1), ...args], { encoding: 'utf8', env: process.env });
    if (result.error) fail('无法启动 gh：' + result.error.message);
    return result;
}

const repo = option('repo');
const tag = option('tag');
const version = option('version');
const title = option('title');
const assets = options('asset');
const changelog = option('changelog');
const commit = option('commit');
const runUrl = option('run-url');
const scope = option('scope');

const tagPattern = /^v?\d+\.\d+\.\d+(?:[.-](alpha|beta|rc)[.-]?\d+)?$/;
const tagMatch = tagPattern.exec(tag);
if (!tagMatch) fail('tag 格式无效：' + tag);
if (tag.replace(/^v/, '') !== version) fail('tag ' + tag + ' 与版本 ' + version + ' 不一致');
for (const asset of assets) if (!existsSync(asset)) fail('构建资产不存在：' + asset);
if (!existsSync(changelog)) fail('静态说明不存在：' + changelog);

const auth = gh(['auth', 'status']);
if (auth.status !== 0) fail('GitHub CLI 未认证：' + (auth.stderr || '').trim());

const prerelease = Boolean(tagMatch[1]);
const tick = String.fromCharCode(96);
const releaseBody = [
    readFileSync(changelog, 'utf8').trim(),
    '',
    '---',
    '',
    '## 构建证据',
    '',
    '- tag：' + tick + tag + tick,
    '- 版本：' + tick + version + tick,
    '- 提交：' + tick + commit + tick,
    '- Actions 运行：' + runUrl,
    '- 构建范围：' + scope,
    '- 发布资产：' + assets.map(asset => tick + basename(asset) + tick).join('、'),
    ''
].join('\n');

const bodyPath = join(tmpdir(), 'qz-uilib-release-' + process.pid + '.md');
writeFileSync(bodyPath, releaseBody, 'utf8');

try {
    const view = gh(['release', 'view', tag, '--repo', repo, '--json', 'tagName']);
    let created = false;
    if (view.status !== 0) {
        const details = (view.stderr || '') + '\n' + (view.stdout || '');
        if (!/release not found|HTTP 404/i.test(details)) {
            fail('查询 Release 失败（非 404）：' + details.trim());
        }
        const createArgs = ['release', 'create', tag, '--repo', repo, '--verify-tag', '--draft', '--title', title,
            '--notes-file', bodyPath, '--latest=false'];
        if (prerelease) createArgs.push('--prerelease');
        const create = gh(createArgs);
        if (create.status !== 0) fail('创建草稿 Release 失败：' + (create.stderr || '').trim());
        created = true;
        console.log('已创建草稿 Release：' + tag);
    } else {
        console.log('Release 已存在，更新同名资产：' + tag);
    }

    const upload = gh(['release', 'upload', tag, ...assets, '--repo', repo, '--clobber']);
    if (upload.status !== 0) fail('上传资产失败：' + (upload.stderr || '').trim());
    console.log('已上传资产：' + assets.map(asset => basename(asset)).join('、'));

    const edit = gh(['release', 'edit', tag, '--repo', repo, '--title', title,
        '--notes-file', bodyPath, '--prerelease=' + prerelease, '--latest=' + (!prerelease), '--draft=false']);
    if (edit.status !== 0) fail('更新 Release 属性失败：' + (edit.stderr || '').trim());
    console.log((created ? '已公开 Release：' : '已更新 Release：') + tag);
} finally {
    rmSync(bodyPath, { force: true });
}
