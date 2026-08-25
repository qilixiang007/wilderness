/**
 * 静态回退数据：字段形状与后端 API DTO 保持一致（imageAltZh/imageAltEn、objectCount），
 * 供后端不可达时的离线模式渲染使用。
 */
export const celestialCategories = [
	{
		slug: 'star',
		zhName: '恒星',
		enName: 'Stars',
		zhDescription: '恒星是发光发热的巨大天体，太阳就是最熟悉的例子。',
		enDescription: 'Stars are massive luminous bodies. The Sun is the most familiar example.',
		image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/b/b4/The_Sun_by_the_Atmospheric_Imaging_Assembly_of_NASA%27s_Solar_Dynamics_Observatory_-_20100819.jpg/1280px-The_Sun_by_the_Atmospheric_Imaging_Assembly_of_NASA%27s_Solar_Dynamics_Observatory_-_20100819.jpg',
		imageAltZh: '太阳的高清照片',
		imageAltEn: 'High-resolution image of the Sun',
		objectCount: 3
	},
	{
		slug: 'planet',
		zhName: '行星',
		enName: 'Planets',
		zhDescription: '围绕恒星运行的天体，例如地球、火星、木星。',
		enDescription: 'Bodies that orbit a star, such as Earth, Mars, and Jupiter.',
		image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/9/97/The_Earth_seen_from_Apollo_17.jpg/1280px-The_Earth_seen_from_Apollo_17.jpg',
		imageAltZh: '从太空看到的地球',
		imageAltEn: 'Earth as seen from space',
		objectCount: 4
	},
	{
		slug: 'moon',
		zhName: '卫星',
		enName: 'Moons',
		zhDescription: '围绕行星运行的天体，月球是最典型的天然卫星。',
		enDescription: 'Bodies that orbit planets. The Moon is the classic natural satellite.',
		image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e1/FullMoon2010.jpg/1280px-FullMoon2010.jpg',
		imageAltZh: '月球的照片',
		imageAltEn: 'Photo of the Moon',
		objectCount: 3
	},
	{
		slug: 'galaxy',
		zhName: '星系',
		enName: 'Galaxies',
		zhDescription: '由大量恒星、气体、尘埃和暗物质组成的巨大系统。',
		enDescription: 'Huge systems of stars, gas, dust, and dark matter bound together.',
		image: 'https://images-assets.nasa.gov/image/NGC_1300/NGC_1300~orig.jpg',
		imageAltZh: '壮观的星系照片',
		imageAltEn: 'A striking photograph of a galaxy',
		objectCount: 2
	},
	{
		slug: 'nebula',
		zhName: '星云',
		enName: 'Nebulae',
		zhDescription: '由气体和尘埃构成的弥漫天体，常与恒星诞生有关。',
		enDescription: 'Diffuse clouds of gas and dust often linked to stellar birth.',
		image: 'https://images-assets.nasa.gov/image/GSFC_20171208_Archive_e000600/GSFC_20171208_Archive_e000600~orig.jpg',
		imageAltZh: '壮丽的星云照片',
		imageAltEn: 'Vivid image of a nebula',
		objectCount: 2
	},
	{
		slug: 'small-bodies',
		zhName: '彗星与小天体',
		enName: 'Comets and small bodies',
		zhDescription: '包括彗星、小行星等日常也常听到的天体名称。',
		enDescription: 'Comets, asteroids, and other celestial names people hear in daily life.',
		image: 'https://images-assets.nasa.gov/image/PIA18695/PIA18695~orig.jpg',
		imageAltZh: '彗星与小天体的宇宙照片',
		imageAltEn: 'Astronomical image of a comet and small bodies',
		objectCount: 2
	}
]
