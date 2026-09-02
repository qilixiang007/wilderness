#!/usr/bin/env node
/**
 * i18n 同步校验：zh.js 与 en.js 的 key 树必须完全一致（仅 value 可不同）。
 *
 * 为什么需要：文案跨两份字典手动维护，加/改 key 时只动一边就会「漂移」——
 * 缺 key 的语言界面会直接渲染原始 key（如 auth.emailPlaceholder），而且静默发生。
 * 本脚本在提交前挡住漂移：只在 zh 或缺在 en（反之亦然）的 key 都会报错退出(exit 1)。
 *
 * 用法：node scripts/check-i18n.mjs      （或 npm run check:i18n）
 * 说明：仅比对 key 结构，不校验翻译质量；数组 key 视作叶子。
 */
import { fileURLToPath, pathToFileURL } from 'node:url'
import { dirname, join } from 'node:path'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
// 用 import 求值 locale 模块（纯数据，无副作用）。
// pathToFileURL：Windows 下 join() 产出盘符路径(e:\...)，ESM import() 需 file:// URL。
const load = (rel) => import(pathToFileURL(join(root, rel))).then((m) => m.default)
const zh = await load('frontend/src/i18n/locales/zh.js')
const en = await load('frontend/src/i18n/locales/en.js')

/** 收集所有叶子/数组 key 的完整点路径（命名空间天然包含在内） */
function leaves(obj, prefix = '', out = []) {
	for (const [k, v] of Object.entries(obj)) {
		const path = prefix ? `${prefix}.${k}` : k
		if (v && typeof v === 'object' && !Array.isArray(v)) leaves(v, path, out)
		else out.push(path)
	}
	return out
}

const zk = leaves(zh).sort()
const ek = leaves(en).sort()
const onlyZh = zk.filter((k) => !ek.includes(k))
const onlyEn = ek.filter((k) => !zk.includes(k))

if (onlyZh.length || onlyEn.length) {
	console.error(`✗ i18n 漂移：zh ${zk.length} keys / en ${ek.length} keys`)
	if (onlyZh.length) console.error(`  仅在 zh（en 缺失）: ${onlyZh.map((k) => k).join(', ')}`)
	if (onlyEn.length) console.error(`  仅在 en（zh 缺失）: ${onlyEn.map((k) => k).join(', ')}`)
	process.exit(1)
}
console.log(`✓ i18n 同步 OK（zh/en 各 ${zk.length} keys，结构一致）`)
