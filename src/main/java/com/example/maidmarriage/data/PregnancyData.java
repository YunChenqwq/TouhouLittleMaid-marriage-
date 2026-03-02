package com.example.maidmarriage.data;

import com.example.maidmarriage.config.ModConfigs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 生理/怀孕数据结构：记录首次经历、怀孕状态与心情计算。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public record PregnancyData(
        boolean pregnant,
        Optional<UUID> father,
        long conceivedGameTime,
        boolean firstExperience,
        boolean firstBirth,
        long lastRomanceDay) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final long NO_ROMANCE_DAY = -1L;

    public static final PregnancyData EMPTY = new PregnancyData(false, Optional.empty(), 0L, false, false, NO_ROMANCE_DAY);

    public static final Codec<PregnancyData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("pregnant", false).forGetter(PregnancyData::pregnant),
                    UUID_CODEC.optionalFieldOf("father").forGetter(PregnancyData::father),
                    Codec.LONG.optionalFieldOf("conceived_game_time", 0L).forGetter(PregnancyData::conceivedGameTime),
                    Codec.BOOL.optionalFieldOf("first_experience", false).forGetter(PregnancyData::firstExperience),
                    Codec.BOOL.optionalFieldOf("first_birth", false).forGetter(PregnancyData::firstBirth),
                    Codec.LONG.optionalFieldOf("last_romance_day", NO_ROMANCE_DAY).forGetter(PregnancyData::lastRomanceDay)
            ).apply(instance, PregnancyData::new));

    public PregnancyData conceive(UUID fatherId, long gameTime) {
        return new PregnancyData(true, Optional.of(fatherId), gameTime, firstExperience, firstBirth, lastRomanceDay);
    }

    public PregnancyData clear() {
        return new PregnancyData(false, Optional.empty(), 0L, firstExperience, firstBirth, lastRomanceDay);
    }

    public PregnancyData markRomance(long gameTime) {
        return new PregnancyData(pregnant, father, conceivedGameTime, true, firstBirth, gameTime / 24000L);
    }

    public PregnancyData forceLonging(long gameTime) {
        long currentDay = gameTime / 24000L;
        long longingDay = currentDay - Math.max(1L, ModConfigs.longingDays());
        return new PregnancyData(pregnant, father, conceivedGameTime, true, firstBirth, longingDay);
    }

    public PregnancyData completeBirth() {
        return new PregnancyData(false, Optional.empty(), 0L, true, true, lastRomanceDay);
    }

    public boolean isPregnantWith(UUID fatherId) {
        return this.pregnant && this.father.filter(fatherId::equals).isPresent();
    }

    public Optional<MoodState> currentMood(long gameTime) {
        if (!firstExperience || lastRomanceDay == NO_ROMANCE_DAY) {
            return Optional.empty();
        }
        long currentDay = gameTime / 24000L;
        long missingDays = Math.max(0L, currentDay - lastRomanceDay);
        if (missingDays == 0) {
            return Optional.of(MoodState.CALM);
        }
        if (missingDays == 1) {
            return Optional.of(MoodState.NORMAL);
        }
        if (missingDays < Math.max(2L, ModConfigs.longingDays())) {
            return Optional.of(MoodState.NORMAL);
        }
        return Optional.of(MoodState.LONGING);
    }

    public enum MoodState {
        CALM,
        NORMAL,
        LONGING;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
