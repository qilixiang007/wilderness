package com.wilderness.backend.nasa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.domain.CelestialObject;
import com.wilderness.backend.domain.ObjectFact;
import com.wilderness.backend.repository.CelestialObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/**
 * 权威数据落地：包内官方页面快照目录（resources/nasa/fact-sheet-snapshots.json，2026-09 核实），
 * 现覆盖太阳、地球/火星/木星/土星四行星、月球/泰坦/欧罗巴三颗卫星，以及此前科普自采数值的
 * 天狼星/参宿四两颗恒星、银河系/仙女座两星系、猎户座/蟹状两星云、哈雷彗星与矮行星谷神星
 * （后八者按各自主源机构如实标注，来源页与数值均已核实）。
 *
 * 启动就绪后（DataSeeder 之后）按 slug 逐个应用：对象还没有来源标记时，把快照里受控的
 * 数值行就地更新（半径覆盖为 NASA 精确值，质量/平均密度/表面重力追加），并给对象打上来源
 * 标注；已带来源标记的对象（如先前应用过的太阳）直接跳过。之后每次启动见标记即跳过，
 * 不再改动数据库。物理参数近静态，仅在首次/无来源标记时应用一次。
 *
 * 不做在线同步：各对象的权威数值经人工逐一核实后打包进快照，离线可靠、可复核。
 * best-effort：单个对象失败只记日志、保留其现有数据，不影响其它对象。
 */
@Service
public class CelestialObjectSyncService {

	private static final Logger log = LoggerFactory.getLogger(CelestialObjectSyncService.class);

	private static final String SNAPSHOT_RESOURCE = "nasa/fact-sheet-snapshots.json";

	private final CelestialObjectRepository celestialObjectRepository;
	private final ObjectMapper objectMapper;
	private final boolean enabled;

	public CelestialObjectSyncService(CelestialObjectRepository celestialObjectRepository,
			ObjectMapper objectMapper,
			@Value("${wilderness.nasa.enabled:false}") boolean enabled) {
		this.celestialObjectRepository = celestialObjectRepository;
		this.objectMapper = objectMapper;
		this.enabled = enabled;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void onReady() {
		if (!enabled) {
			log.info("NASA data apply disabled (wilderness.nasa.enabled=false), skip.");
			return;
		}

		NasaCatalog catalog;
		try {
			catalog = loadCatalog();
		} catch (Exception e) {
			log.warn("NASA snapshot catalog load failed, keeping existing data: {}", e.toString());
			return;
		}

		int updated = 0, skippedSourced = 0, missing = 0;
		for (Snapshot snapshot : catalog.snapshots()) {
			CelestialObject object = celestialObjectRepository.findBySlug(snapshot.slug()).orElse(null);
			if (object == null) {
				log.info("NASA snapshot: object '{}' not found, skip.", snapshot.slug());
				missing++;
				continue;
			}
			if (object.getSourcedAt() != null) {
				skippedSourced++;
				continue;
			}
			try {
				int applied = applyFacts(object, snapshot.facts());
				if (applied > 0) {
					object.markSourced(snapshot.dataSource(), snapshot.sourceUrl(), Instant.now());
					log.info("Applied NASA snapshot to '{}' ({} of {} rows), source at {}.",
							snapshot.slug(), applied, snapshot.facts().size(), object.getSourcedAt());
					updated++;
				} else {
					log.warn("NASA snapshot '{}' matched 0 rows; keep existing data, no source mark.",
							snapshot.slug());
				}
			} catch (Exception e) {
				log.warn("NASA snapshot '{}' apply failed, keeping existing data: {}",
						snapshot.slug(), e.toString());
			}
		}
		log.info("NASA snapshot apply done: {} updated, {} already sourced, {} not found.",
				updated, skippedSourced, missing);
	}

	/** 逐卡片合并：zhLabel 命中种子行则原位改值，否则追加新卡片。返回实际应用了几行。 */
	private int applyFacts(CelestialObject object, List<Fact> facts) {
		int applied = 0;
		for (Fact fact : facts) {
			ObjectFact existing = object.getFacts().stream()
					.filter(f -> fact.zhLabel().equals(f.getZhLabel()))
					.findFirst()
					.orElse(null);
			if (existing != null) {
				existing.updateValue(fact.zhValue(), fact.enValue());
			} else {
				object.addFact(new ObjectFact(fact.sortOrder(), fact.zhLabel(), fact.enLabel(),
						fact.zhValue(), fact.enValue()));
			}
			applied++;
		}
		return applied;
	}

	private NasaCatalog loadCatalog() throws IOException {
		try (InputStream in = new ClassPathResource(SNAPSHOT_RESOURCE).getInputStream()) {
			return objectMapper.readValue(in, NasaCatalog.class);
		}
	}

	private record NasaCatalog(List<Snapshot> snapshots) {
	}

	private record Snapshot(String slug, String dataSource, String sourceUrl, List<Fact> facts) {
	}

	private record Fact(int sortOrder, String zhLabel, String enLabel, String zhValue, String enValue) {
	}
}
