package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

val DEFAULT_RELAY_PROVIDER_ID = Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e")

val DEFAULT_PROVIDERS = listOf(
    ProviderSetting.OpenAI(
        id = DEFAULT_RELAY_PROVIDER_ID,
        name = "Relay Gateway (@model-aggregator)",
        baseUrl = "https://relay-gw.pages.dev/v1",
        chatCompletionsPath = "/chat/completions",
        enabled = true,
        builtIn = true,
        models = listOf(
            Model(
                id = Uuid.parse("11111111-1111-1111-1111-111111111101"),
                modelId = "deepseek-v4-1-flash",
                displayName = "DeepSeek V4.1 Flash",
                type = ModelType.CHAT,
                abilities = listOf(ModelAbility.REASONING),
                contextWindowTokens = 128000,
                maxOutputTokens = 64000
            ),
            Model(
                id = Uuid.parse("11111111-1111-1111-1111-111111111102"),
                modelId = "auto",
                displayName = "Auto Router",
                type = ModelType.CHAT,
                contextWindowTokens = 2000000,
                maxOutputTokens = 64000
            ),
            Model(
                id = Uuid.parse("11111111-1111-1111-1111-111111111103"),
                modelId = "gpt-6-astra",
                displayName = "GPT-6 Astra",
                type = ModelType.CHAT,
                abilities = listOf(ModelAbility.REASONING),
                contextWindowTokens = 128000,
                maxOutputTokens = 64000
            ),
            Model(
                id = Uuid.parse("11111111-1111-1111-1111-111111111104"),
                modelId = "gpt-5-5",
                displayName = "GPT-5.5",
                type = ModelType.CHAT,
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                contextWindowTokens = 256000,
                maxOutputTokens = 64000
            ),
            Model(
                id = Uuid.parse("11111111-1111-1111-1111-111111111105"),
                modelId = "claude-opus-5",
                displayName = "Claude Opus 5",
                type = ModelType.CHAT,
                abilities = listOf(ModelAbility.REASONING),
                contextWindowTokens = 128000,
                maxOutputTokens = 64000
            ),
            Model(
                id = Uuid.parse("11111111-1111-1111-1111-111111111106"),
                modelId = "gemini-3-8-flash",
                displayName = "Gemini 3.8 Flash",
                type = ModelType.CHAT,
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                contextWindowTokens = 1000000,
                maxOutputTokens = 64000
            ),
            Model(
                id = Uuid.parse("11111111-1111-1111-1111-111111111107"),
                modelId = "qwen3-8-flash",
                displayName = "Qwen 3.8 Flash",
                type = ModelType.CHAT,
                abilities = listOf(ModelAbility.REASONING),
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                contextWindowTokens = 1000000,
                maxOutputTokens = 131072
            ),
            Model(
                id = Uuid.parse("11111111-1111-1111-1111-111111111108"),
                modelId = "qwen3-8-max",
                displayName = "Qwen 3.8 Max",
                type = ModelType.CHAT,
                abilities = listOf(ModelAbility.REASONING),
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                contextWindowTokens = 128000,
                maxOutputTokens = 64000
            ),
            Model(
                id = Uuid.parse("11111111-1111-1111-1111-111111111109"),
                modelId = "mimo-v2-6-pro",
                displayName = "MiMo V2.6 Pro",
                type = ModelType.CHAT,
                abilities = listOf(ModelAbility.REASONING),
                contextWindowTokens = 1050000,
                maxOutputTokens = 131072
            ),
            Model(
                id = Uuid.parse("11111111-1111-1111-1111-111111111110"),
                modelId = "instant",
                displayName = "Instant Fast",
                type = ModelType.CHAT,
                contextWindowTokens = 64000,
                maxOutputTokens = 16384
            )
        )
    )
)
