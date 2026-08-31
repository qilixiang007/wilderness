package com.wilderness.backend.config;

import com.wilderness.backend.domain.Category;
import com.wilderness.backend.domain.CelestialObject;
import com.wilderness.backend.domain.ObjectFact;
import com.wilderness.backend.repository.CategoryRepository;
import com.wilderness.backend.repository.CelestialObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 库为空时播种基础内容：6 个分类 + 16 个天体及其双语数据卡片。
 * 开发环境（MySQL，ddl-auto=update）只在首次启动播种一次；测试环境（H2，create-drop）每次全新播种。
 */
@Component
public class DataSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

	private final CategoryRepository categoryRepository;
	private final CelestialObjectRepository celestialObjectRepository;

	public DataSeeder(CategoryRepository categoryRepository, CelestialObjectRepository celestialObjectRepository) {
		this.categoryRepository = categoryRepository;
		this.celestialObjectRepository = celestialObjectRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		if (categoryRepository.count() > 0) {
			log.info("DataSeeder skipped: categories already present.");
			return;
		}

		Map<String, Category> categories = seedCategories();
		seedObjects(categories);

		log.info("Seeded {} categories, {} celestial objects.",
				categoryRepository.count(), celestialObjectRepository.count());
	}

	// ---- 图片：全部为本地静态资源（backend/src/main/resources/static/images/，经 /images/* 提供），
	// 不依赖外部图床，离线可用。分类用代表图；天体尽量用各自真实照片。 ----

	private static final String IMG_STAR = "/images/sun.jpg";
	private static final String IMG_SIRIUS = "/images/sirius.jpg";
	private static final String IMG_BETELGEUSE = "/images/betelgeuse.jpg";
	private static final String IMG_PLANET = "/images/earth.jpg";
	private static final String IMG_MARS = "/images/mars.jpg";
	private static final String IMG_JUPITER = "/images/jupiter.jpg";
	private static final String IMG_SATURN = "/images/saturn.jpg";
	private static final String IMG_MOON = "/images/moon.jpg";
	private static final String IMG_TITAN = "/images/titan.jpg";
	private static final String IMG_EUROPA = "/images/europa.jpg";
	private static final String IMG_GALAXY = "/images/galaxy.jpg";
	private static final String IMG_NEBULA = "/images/nebula.jpg";
	private static final String IMG_SMALL = "/images/comet.jpg";

	private Map<String, Category> seedCategories() {
		Map<String, Category> bySlug = new LinkedHashMap<>();
		bySlug.put("star", categoryRepository.save(new Category(
				"star", "恒星", "Stars",
				"恒星是发光发热的巨大天体，太阳就是最熟悉的例子。",
				"Stars are massive luminous bodies. The Sun is the most familiar example.",
				IMG_STAR, "太阳的高清照片", "High-resolution image of the Sun", 0)));
		bySlug.put("planet", categoryRepository.save(new Category(
				"planet", "行星", "Planets",
				"围绕恒星运行的天体，例如地球、火星、木星。",
				"Bodies that orbit a star, such as Earth, Mars, and Jupiter.",
				IMG_PLANET, "从太空看到的地球", "Earth as seen from space", 1)));
		bySlug.put("moon", categoryRepository.save(new Category(
				"moon", "卫星", "Moons",
				"围绕行星运行的天体，月球是最典型的天然卫星。",
				"Bodies that orbit planets. The Moon is the classic natural satellite.",
				IMG_MOON, "月球的照片", "Photo of the Moon", 2)));
		bySlug.put("galaxy", categoryRepository.save(new Category(
				"galaxy", "星系", "Galaxies",
				"由大量恒星、气体、尘埃和暗物质组成的巨大系统。",
				"Huge systems of stars, gas, dust, and dark matter bound together.",
				IMG_GALAXY, "壮观的星系照片", "A striking photograph of a galaxy", 3)));
		bySlug.put("nebula", categoryRepository.save(new Category(
				"nebula", "星云", "Nebulae",
				"由气体和尘埃构成的弥漫天体，常与恒星诞生有关。",
				"Diffuse clouds of gas and dust often linked to stellar birth.",
				IMG_NEBULA, "壮丽的星云照片", "Vivid image of a nebula", 4)));
		bySlug.put("small-bodies", categoryRepository.save(new Category(
				"small-bodies", "彗星与小天体", "Comets and small bodies",
				"包括彗星、小行星等日常也常听到的天体名称。",
				"Comets, asteroids, and other celestial names people hear in daily life.",
				IMG_SMALL, "彗星与小天体的宇宙照片", "Astronomical image of a comet and small bodies", 5)));
		return bySlug;
	}

	private void seedObjects(Map<String, Category> categories) {
		save(object("sun", "太阳", "Sun",
				"太阳是太阳系中心的恒星，为地球提供光和热，也是人类认识的第一颗恒星。",
				"The Sun is the star at the center of the Solar System, providing Earth with light and heat.",
				IMG_STAR, 0, categories.get("star"),
				fact(1, "类型", "Type", "恒星", "Star"),
				fact(2, "距地球", "Distance from Earth", "约 1.496 亿千米", "about 149.6 million km"),
				fact(3, "半径", "Radius", "约 696,340 千米", "about 696,340 km"),
				fact(4, "表面温度", "Surface temperature", "约 5,500 °C", "about 5,500 °C"),
				fact(5, "年龄", "Age", "约 46 亿年", "about 4.6 billion years")));

		save(object("sirius", "天狼星", "Sirius",
				"天狼星是夜空中最亮的恒星，位于大犬座，距地球约 8.6 光年。",
				"Sirius is the brightest star in the night sky, located in Canis Major about 8.6 light-years away.",
				IMG_SIRIUS, 1, categories.get("star"),
				fact(1, "类型", "Type", "恒星", "Star"),
				fact(2, "星座", "Constellation", "大犬座", "Canis Major"),
				fact(3, "距地球", "Distance from Earth", "约 8.6 光年", "about 8.6 light-years"),
				fact(4, "特点", "Notable for", "全天最亮的恒星", "the brightest star in the night sky")));

		save(object("betelgeuse", "参宿四", "Betelgeuse",
				"参宿四是猎户座肩部的红超巨星，体积巨大，正处于演化后期。",
				"Betelgeuse is a red supergiant on Orion's shoulder, a massive star in its late evolutionary stage.",
				IMG_BETELGEUSE, 2, categories.get("star"),
				fact(1, "类型", "Type", "红超巨星", "Red supergiant"),
				fact(2, "距地球", "Distance from Earth", "约 642 光年", "about 642 light-years"),
				fact(3, "半径", "Radius", "约为太阳的 700 倍", "about 700 times the Sun's radius"),
				fact(4, "位置", "Location", "猎户座肩部", "shoulder of Orion")));

		save(object("earth", "地球", "Earth",
				"地球是太阳系第三颗行星，也是目前已知唯一存在生命的星球。",
				"Earth is the third planet from the Sun and the only world known to host life.",
				IMG_PLANET, 0, categories.get("planet"),
				fact(1, "类型", "Type", "岩质行星", "Terrestrial planet"),
				fact(2, "距太阳", "Distance from Sun", "约 1.496 亿千米", "about 149.6 million km"),
				fact(3, "半径", "Radius", "约 6,371 千米", "about 6,371 km"),
				fact(4, "公转周期", "Orbital period", "约 365.25 天", "about 365.25 days"),
				fact(5, "卫星数量", "Moons", "1", "1")));

		save(object("mars", "火星", "Mars",
				"火星是太阳系第四颗行星，因表面含铁氧化物而呈红色，是人类探测最多的星球之一。",
				"Mars is the fourth planet from the Sun, red from iron oxide, and one of the most explored worlds.",
				IMG_MARS, 1, categories.get("planet"),
				fact(1, "类型", "Type", "岩质行星", "Terrestrial planet"),
				fact(2, "距太阳", "Distance from Sun", "约 2.28 亿千米", "about 228 million km"),
				fact(3, "半径", "Radius", "约 3,390 千米", "about 3,390 km"),
				fact(4, "公转周期", "Orbital period", "约 687 天", "about 687 days"),
				fact(5, "卫星数量", "Moons", "2", "2")));

		save(object("jupiter", "木星", "Jupiter",
				"木星是太阳系最大的行星，以其巨大的气态球体和标志性的大红斑著称。",
				"Jupiter is the largest planet in the Solar System, famous for its Great Red Spot.",
				IMG_JUPITER, 2, categories.get("planet"),
				fact(1, "类型", "Type", "气态巨行星", "Gas giant"),
				fact(2, "距太阳", "Distance from Sun", "约 7.78 亿千米", "about 778 million km"),
				fact(3, "半径", "Radius", "约 69,911 千米", "about 69,911 km"),
				fact(4, "公转周期", "Orbital period", "约 11.9 年", "about 11.9 years"),
				fact(5, "卫星数量", "Moons", "已知 95 颗", "95 known moons")));

		save(object("saturn", "土星", "Saturn",
				"土星以壮丽的光环闻名，是太阳系中密度最低的行星之一。",
				"Saturn is renowned for its magnificent rings and is one of the least dense planets.",
				IMG_SATURN, 3, categories.get("planet"),
				fact(1, "类型", "Type", "气态巨行星", "Gas giant"),
				fact(2, "距太阳", "Distance from Sun", "约 14.3 亿千米", "about 1.43 billion km"),
				fact(3, "半径", "Radius", "约 58,232 千米", "about 58,232 km"),
				fact(4, "公转周期", "Orbital period", "约 29.5 年", "about 29.5 years"),
				fact(5, "卫星数量", "Moons", "已知 146 颗", "146 known moons")));

		save(object("moon", "月球", "The Moon",
				"月球是地球唯一的天然卫星，也是人类唯一亲身踏足的地外天体。",
				"The Moon is Earth's only natural satellite and the only other world humans have visited.",
				IMG_MOON, 0, categories.get("moon"),
				fact(1, "类型", "Type", "天然卫星", "Natural satellite"),
				fact(2, "距地球", "Distance from Earth", "约 384,400 千米", "about 384,400 km"),
				fact(3, "半径", "Radius", "约 1,737 千米", "about 1,737 km"),
				fact(4, "公转周期", "Orbital period", "约 27.3 天", "about 27.3 days")));

		save(object("titan", "泰坦", "Titan",
				"泰坦是土星最大的卫星，拥有浓厚大气和甲烷湖泊，被视为寻找生命迹象的重要目标。",
				"Titan is Saturn's largest moon, with a thick atmosphere and methane lakes.",
				IMG_TITAN, 1, categories.get("moon"),
				fact(1, "类型", "Type", "天然卫星", "Natural satellite"),
				fact(2, "属于", "Orbits", "土星", "Saturn"),
				fact(3, "半径", "Radius", "约 2,575 千米", "about 2,575 km"),
				fact(4, "特点", "Notable for", "浓厚大气与液态甲烷湖泊", "a thick atmosphere and lakes of liquid methane")));

		save(object("europa", "欧罗巴", "Europa",
				"欧罗巴是木星的一颗冰卫星，科学家认为其冰壳下存在液态海洋。",
				"Europa is an icy moon of Jupiter thought to hide a liquid ocean beneath its crust.",
				IMG_EUROPA, 2, categories.get("moon"),
				fact(1, "类型", "Type", "天然卫星", "Natural satellite"),
				fact(2, "属于", "Orbits", "木星", "Jupiter"),
				fact(3, "半径", "Radius", "约 1,560 千米", "about 1,560 km"),
				fact(4, "特点", "Notable for", "冰壳下可能藏着液态海洋", "a subsurface ocean may lie beneath its icy crust")));

		save(object("milky-way", "银河系", "Milky Way",
				"银河系是太阳系所在的星系，从地球看去呈一条横贯夜空的银白色光带。",
				"The Milky Way is the galaxy containing the Solar System, seen as a bright band across the night sky.",
				IMG_GALAXY, 0, categories.get("galaxy"),
				fact(1, "类型", "Type", "棒旋星系", "Barred spiral galaxy"),
				fact(2, "直径", "Diameter", "约 10 万光年", "about 100,000 light-years"),
				fact(3, "恒星数量", "Star count", "约 1000 亿到 4000 亿颗", "about 100 to 400 billion stars"),
				fact(4, "位置", "Location", "本星系群", "Local Group")));

		save(object("andromeda", "仙女座星系", "Andromeda Galaxy",
				"仙女座星系是距离银河系最近的大型星系，也是肉眼可见的最远天体之一。",
				"The Andromeda Galaxy is the nearest large galaxy to the Milky Way.",
				IMG_GALAXY, 1, categories.get("galaxy"),
				fact(1, "类型", "Type", "旋涡星系", "Spiral galaxy"),
				fact(2, "距地球", "Distance from Earth", "约 250 万光年", "about 2.5 million light-years"),
				fact(3, "直径", "Diameter", "约 22 万光年", "about 220,000 light-years"),
				fact(4, "特点", "Notable for", "距银河系最近的大星系", "the nearest large galaxy")));

		save(object("orion-nebula", "猎户座星云", "Orion Nebula",
				"猎户座星云是猎户座中的恒星诞生区，是夜空肉眼可见最亮的星云之一。",
				"The Orion Nebula is a stellar nursery in Orion and one of the brightest nebulae visible to the naked eye.",
				IMG_NEBULA, 0, categories.get("nebula"),
				fact(1, "类型", "Type", "发射星云", "Emission nebula"),
				fact(2, "距地球", "Distance from Earth", "约 1,344 光年", "about 1,344 light-years"),
				fact(3, "位置", "Location", "猎户座", "Orion"),
				fact(4, "特点", "Notable for", "肉眼可见的恒星摇篮", "a visible stellar nursery")));

		save(object("crab-nebula", "蟹状星云", "Crab Nebula",
				"蟹状星云是公元 1054 年观测到的超新星爆炸后的遗迹，中心有一颗脉冲星。",
				"The Crab Nebula is the remnant of a supernova observed in AD 1054, with a pulsar at its heart.",
				IMG_NEBULA, 1, categories.get("nebula"),
				fact(1, "类型", "Type", "超新星遗迹", "Supernova remnant"),
				fact(2, "距地球", "Distance from Earth", "约 6,500 光年", "about 6,500 light-years"),
				fact(3, "位置", "Location", "金牛座", "Taurus"),
				fact(4, "特点", "Notable for", "公元 1054 年超新星的遗迹", "remnant of a supernova seen in AD 1054")));

		save(object("halleys-comet", "哈雷彗星", "Halley's Comet",
				"哈雷彗星是最著名的周期彗星，约每 76 年回归一次，最近一次回归在 1986 年。",
				"Halley's Comet is the most famous periodic comet, returning about every 76 years.",
				IMG_SMALL, 0, categories.get("small-bodies"),
				fact(1, "类型", "Type", "彗星", "Comet"),
				fact(2, "公转周期", "Orbital period", "约 76 年", "about 76 years"),
				fact(3, "距太阳最近点", "Perihelion", "约 8,800 万千米", "about 88 million km"),
				fact(4, "下次回归", "Next return", "2061 年", "2061")));

		save(object("ceres", "谷神星", "Ceres",
				"谷神星是小行星带中最大的天体，已被归为矮行星。",
				"Ceres is the largest body in the asteroid belt and is classified as a dwarf planet.",
				IMG_SMALL, 1, categories.get("small-bodies"),
				fact(1, "类型", "Type", "矮行星", "Dwarf planet"),
				fact(2, "距太阳", "Distance from Sun", "约 4.14 亿千米", "about 414 million km"),
				fact(3, "半径", "Radius", "约 476 千米", "about 476 km"),
				fact(4, "位置", "Location", "小行星带", "main asteroid belt")));
	}

	// ---- 构造辅助 ----

	private CelestialObject object(String slug, String zhName, String enName, String zhDescription,
			String enDescription, String image, int sortOrder, Category category, ObjectFact... facts) {
		CelestialObject celestialObject = new CelestialObject(slug, zhName, enName, zhDescription,
				enDescription, image, sortOrder, category);
		for (ObjectFact fact : facts) {
			celestialObject.addFact(fact);
		}
		return celestialObject;
	}

	private ObjectFact fact(int sortOrder, String zhLabel, String enLabel, String zhValue, String enValue) {
		return new ObjectFact(sortOrder, zhLabel, enLabel, zhValue, enValue);
	}

	private void save(CelestialObject celestialObject) {
		celestialObjectRepository.save(celestialObject);
	}
}
