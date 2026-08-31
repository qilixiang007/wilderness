// 英文字典：key 与 zh.js 一一对应。
export default {
	site: {
		title: 'Wilderness of the Universe'
	},
	nav: {
		home: 'Home',
		categories: 'Index',
		planets: 'Planets',
		ask: 'Ask AI',
		search: 'Search',
		favorites: 'Favorites'
	},
	common: {
		loading: 'Loading…',
		retry: 'Retry',
		back: '← Go back',
		viewDetails: 'View details',
		sources: 'Sources',
		loadFailed: 'Failed to load. Please retry.',
		objects: '{n} objects'
	},
	home: {
		heroKicker: 'A FIELD GUIDE TO THE COSMOS',
		headline: 'Explaining the universe to everyone.',
		description: 'A bilingual tour of the night sky — from stars to nebulae, browsable by category and rich with data and stories.',
		exploreNow: 'Explore now',
		startWithPlanets: 'Start with planets',
		visitSun: 'Visit the Sun',
		ask: 'Ask AI',
		catalogued: 'CATALOGUED',
		stats: '{categories} categories · {objects} objects',
		statsFallback: '6 categories · 16 objects',
		observerNotes: "OBSERVER'S NOTES",
		whereToStart: 'Where to start',
		intro: 'From the constellations overhead to galaxies billions of light-years away, get to know the wilderness above. Start by category, or jump straight to an object you are curious about.',
		fromBackend: 'From backend: {categories} categories · {objects} objects'
	},
	category: {
		indexKicker: 'CELESTIAL INDEX',
		title: 'Celestial Categories',
		intro: 'Browse everything in this wilderness, by type.',
		file: 'Category file',
		notFound: 'Category not found.',
		noObjects: 'No objects in this category yet.'
	},
	object: {
		file: 'Object file',
		keyFacts: 'Key facts',
		aiExplainer: 'AI explainer',
		explainPrompt: 'Let AI write a science explainer for this object based on the knowledge base.',
		generate: 'Generate',
		generating: 'AI is writing…',
		generateFailed: 'Failed to generate.',
		notFound: 'Celestial object not found.',
		offline: 'Backend unreachable; no offline data for this object.',
		objects: 'Objects'
	},
	ask: {
		kicker: 'AI ASSISTANT · RAG',
		title: 'Ask the wilderness',
		intro: 'Ask about astronomy. Our assistant grounds its answers in the in-house knowledge base via hybrid retrieval, with cited sources.',
		uploading: 'Uploading…',
		uploadFile: 'Upload file',
		uploaded: 'Ingested "{fileName}" ({chunkCount} chunks)',
		backendUnavailable: 'Backend unreachable.',
		uploadFailed: 'Upload failed.',
		tryAsking: 'Try asking:',
		suggestions: ['Which is the largest planet in the solar system?', 'Why do stars twinkle?', 'What is dark matter?'],
		webSearch: 'Web search',
		placeholder: 'Ask a question about space…',
		thinking: 'Thinking…',
		send: 'Send'
	},
	theme: {
		switcher: 'Theme switcher'
	},
	offline: {
		notice: 'Offline mode: backend unreachable, showing local content.'
	},
	search: {
		kicker: 'SEARCH THE WILDERNESS',
		title: 'Search',
		placeholder: 'Search by object name or description…',
		submit: 'Search',
		emptyHint: 'Enter a keyword to search objects, e.g. "Mars" or "Jupiter".',
		noResults: 'No objects match "{q}".',
		offline: 'Backend unreachable; search unavailable.',
		failed: 'Search failed. Please retry.'
	},
	favorites: {
		title: 'Favorites',
		add: '☆ Favorite',
		remove: '★ Favorited',
		empty: 'Nothing favorited yet. Open an object and tap Favorite.'
	}
}
