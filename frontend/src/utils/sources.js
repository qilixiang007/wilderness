/**
 * AI 讲解的引文来源按 slug 去重：同一天体的知识库文档会被切成多个分块，
 * 每个分块单独生成一条来源（title/slug/type 相同，只有 excerpt 不同），
 * 对用户来说指向同一个详情页，展示上只需保留第一条。
 */
export function dedupeSourcesBySlug(sources) {
	const seen = new Set()
	return (sources || []).filter((s) => {
		if (seen.has(s.slug)) return false
		seen.add(s.slug)
		return true
	})
}
