/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.jerryt92.jrag.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.jerryt92.jrag.model.ModelOptionsUtils;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class for creating strongly-typed Ollama options.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @see <a href=
 * "https://github.com/ollama/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama
 * Valid Parameters and Values</a>
 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama Types</a>
 * @since 0.8.0
 */
@Data
@JsonInclude(Include.NON_NULL)
public class OllamaOptions {

    private static final List<String> NON_SUPPORTED_FIELDS = List.of("model", "format", "keep_alive", "truncate");

    // Following fields are options which must be set when the model is loaded into
    // memory.
    // See: https://github.com/ggerganov/llama.cpp/blob/master/examples/main/README.md

    // @formatter:off

	/**
	 * Whether to use NUMA. (Default: false)
	 */
	@JsonProperty("numa")
	private Boolean useNUMA;

	/**
	 * Sets the size of the context window used to generate the next token. (Default: 2048)
	 */
	@JsonProperty("num_ctx")
	private Integer numCtx;

	/**
	 * Prompt processing maximum batch size. (Default: 512)
	 */
	@JsonProperty("num_batch")
	private Integer numBatch;

	/**
	 * The number of layers to send to the GPU(s). On macOS, it defaults to 1
	 * to enable metal support, 0 to disable.
	 * (Default: -1, which indicates that numGPU should be set dynamically)
	 */
	@JsonProperty("num_gpu")
	private Integer numGPU;

	/**
	 * When using multiple GPUs this option controls which GPU is used
	 * for small tensors for which the overhead of splitting the computation
	 * across all GPUs is not worthwhile. The GPU in question will use slightly
	 * more VRAM to store a scratch buffer for temporary results.
	 * By default, GPU 0 is used.
	 */
	@JsonProperty("main_gpu")
	private Integer mainGPU;

	/**
	 * (Default: false)
	 */
	@JsonProperty("low_vram")
	private Boolean lowVRAM;

	/**
	 * (Default: true)
	 */
	@JsonProperty("f16_kv")
	private Boolean f16KV;

	/**
	 * Return logits for all the tokens, not just the last one.
	 * To enable completions to return logprobs, this must be true.
	 */
	@JsonProperty("logits_all")
	private Boolean logitsAll;

	/**
	 * Load only the vocabulary, not the weights.
	 */
	@JsonProperty("vocab_only")
	private Boolean vocabOnly;

	/**
	 * By default, models are mapped into memory, which allows the system to load only the necessary parts
	 * of the model as needed. However, if the model is larger than your total amount of RAM or if your system is low
	 * on available memory, using mmap might increase the risk of pageouts, negatively impacting performance.
	 * Disabling mmap results in slower load times but may reduce pageouts if you're not using mlock.
	 * Note that if the model is larger than the total amount of RAM, turning off mmap would prevent
	 * the model from loading at all.
	 * (Default: null)
	 */
	@JsonProperty("use_mmap")
	private Boolean useMMap;

	/**
	 * Lock the model in memory, preventing it from being swapped out when memory-mapped.
	 * This can improve performance but trades away some of the advantages of memory-mapping
	 * by requiring more RAM to run and potentially slowing down load times as the model loads into RAM.
	 * (Default: false)
	 */
	@JsonProperty("use_mlock")
	private Boolean useMLock;

	/**
	 * Set the number of threads to use during generation. For optimal performance, it is recommended to set this value
	 * to the number of physical CPU cores your system has (as opposed to the logical number of cores).
	 * Using the correct number of threads can greatly improve performance.
	 * By default, Ollama will detect this value for optimal performance.
	 */
	@JsonProperty("num_thread")
	private Integer numThread;

	// Following fields are predict options used at runtime.

	/**
	 * (Default: 4)
	 */
	@JsonProperty("num_keep")
	private Integer numKeep;

	/**
	 * Sets the random number seed to use for generation. Setting this to a
	 * specific number will make the model generate the same text for the same prompt.
	 * (Default: -1)
	 */
	@JsonProperty("seed")
	private Integer seed;

	/**
	 * Maximum number of tokens to predict when generating text.
	 * (Default: 128, -1 = infinite generation, -2 = fill context)
	 */
	@JsonProperty("num_predict")
	private Integer numPredict;

	/**
	 * Reduces the probability of generating nonsense. A higher value (e.g.
	 * 100) will give more diverse answers, while a lower value (e.g. 10) will be more
	 * conservative. (Default: 40)
	 */
	@JsonProperty("top_k")
	private Integer topK;

	/**
	 * Works together with top-k. A higher value (e.g., 0.95) will lead to
	 * more diverse text, while a lower value (e.g., 0.5) will generate more focused and
	 * conservative text. (Default: 0.9)
	 */
	@JsonProperty("top_p")
	private Double topP;

	/**
	 * Alternative to the top_p, and aims to ensure a balance of quality and variety.
	 * The parameter p represents the minimum probability for a token to be considered,
	 * relative to the probability of the most likely token. For example, with p=0.05 and
	 * the most likely token having a probability of 0.9, logits with a value
	 * less than 0.045 are filtered out. (Default: 0.0)
	 */
	@JsonProperty("min_p")
	private Double minP;

	/**
	 * Tail free sampling is used to reduce the impact of less probable tokens
	 * from the output. A higher value (e.g., 2.0) will reduce the impact more, while a
	 * value of 1.0 disables this setting. (default: 1)
	 */
	@JsonProperty("tfs_z")
	private Float tfsZ;

	/**
	 * (Default: 1.0)
	 */
	@JsonProperty("typical_p")
	private Float typicalP;

	/**
	 * Sets how far back for the model to look back to prevent
	 * repetition. (Default: 64, 0 = disabled, -1 = num_ctx)
	 */
	@JsonProperty("repeat_last_n")
	private Integer repeatLastN;

	/**
	 * The temperature of the model. Increasing the temperature will
	 * make the model answer more creatively. (Default: 0.8)
	 */
	@JsonProperty("temperature")
	private Double temperature;

	/**
	 * Sets how strongly to penalize repetitions. A higher value
	 * (e.g., 1.5) will penalize repetitions more strongly, while a lower value (e.g.,
	 * 0.9) will be more lenient. (Default: 1.1)
	 */
	@JsonProperty("repeat_penalty")
	private Double repeatPenalty;

	/**
	 * (Default: 0.0)
	 */
	@JsonProperty("presence_penalty")
	private Double presencePenalty;

	/**
	 * (Default: 0.0)
	 */
	@JsonProperty("frequency_penalty")
	private Double frequencyPenalty;

	/**
	 * Enable Mirostat sampling for controlling perplexity. (default: 0, 0
	 * = disabled, 1 = Mirostat, 2 = Mirostat 2.0)
	 */
	@JsonProperty("mirostat")
	private Integer mirostat;

	/**
	 * Controls the balance between coherence and diversity of the output.
	 * A lower value will result in more focused and coherent text. (Default: 5.0)
	 */
	@JsonProperty("mirostat_tau")
	private Float mirostatTau;

	/**
	 * Influences how quickly the algorithm responds to feedback from the generated text.
	 * A lower learning rate will result in slower adjustments, while a higher learning rate
	 * will make the algorithm more responsive. (Default: 0.1)
	 */
	@JsonProperty("mirostat_eta")
	private Float mirostatEta;

	/**
	 * (Default: true)
	 */
	@JsonProperty("penalize_newline")
	private Boolean penalizeNewline;

	/**
	 * Sets the stop sequences to use. When this pattern is encountered the
	 * LLM will stop generating text and return. Multiple stop patterns may be set by
	 * specifying multiple separate stop parameters in a modelfile.
	 */
	@JsonProperty("stop")
	private List<String> stop;


	// Following fields are not part of the Ollama Options API but part of the Request.

	/**
	 * NOTE: Synthetic field not part of the official Ollama API.
	 * Used to allow overriding the model name with prompt options.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">parameters</a>.
	 */
	@JsonProperty("model")
	private String model;

	/**
	 * Sets the desired format of output from the LLM. The only valid values are null or "json".
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	@JsonProperty("format")
	private Object format;

	/**
	 * Sets the length of time for Ollama to keep the model loaded. Valid values for this
	 * setting are parsed by <a href="https://pkg.go.dev/time#ParseDuration">ParseDuration in Go</a>.
	 * Part of Chat completion <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#parameters-1">advanced parameters</a>.
	 */
	@JsonProperty("keep_alive")
	private String keepAlive;

	/**
	 * Truncates the end of each input to fit within context length. Returns error if false and context length is exceeded.
	 * Defaults to true.
	 */
	@JsonProperty("truncate")
	private Boolean truncate;

	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Filter out the non-supported fields from the options.
	 * @param options The options to filter.
	 * @return The filtered options.
	 */
	public static Map<String, Object> filterNonSupportedFields(Map<String, Object> options) {
		return options.entrySet().stream()
				.filter(e -> !NON_SUPPORTED_FIELDS.contains(e.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	public static OllamaOptions fromOptions(OllamaOptions fromOptions) {
		return builder()
				.model(fromOptions.getModel())
				.format(fromOptions.getFormat())
				.keepAlive(fromOptions.getKeepAlive())
				.truncate(fromOptions.getTruncate())
				.useNUMA(fromOptions.getUseNUMA())
				.numCtx(fromOptions.getNumCtx())
				.numBatch(fromOptions.getNumBatch())
				.numGPU(fromOptions.getNumGPU())
				.mainGPU(fromOptions.getMainGPU())
				.lowVRAM(fromOptions.getLowVRAM())
				.f16KV(fromOptions.getF16KV())
				.logitsAll(fromOptions.getLogitsAll())
				.vocabOnly(fromOptions.getVocabOnly())
				.useMMap(fromOptions.getUseMMap())
				.useMLock(fromOptions.getUseMLock())
				.numThread(fromOptions.getNumThread())
				.numKeep(fromOptions.getNumKeep())
				.seed(fromOptions.getSeed())
				.numPredict(fromOptions.getNumPredict())
				.topK(fromOptions.getTopK())
				.topP(fromOptions.getTopP())
				.minP(fromOptions.getMinP())
				.tfsZ(fromOptions.getTfsZ())
				.typicalP(fromOptions.getTypicalP())
				.repeatLastN(fromOptions.getRepeatLastN())
				.temperature(fromOptions.getTemperature())
				.repeatPenalty(fromOptions.getRepeatPenalty())
				.presencePenalty(fromOptions.getPresencePenalty())
				.frequencyPenalty(fromOptions.getFrequencyPenalty())
				.mirostat(fromOptions.getMirostat())
				.mirostatTau(fromOptions.getMirostatTau())
				.mirostatEta(fromOptions.getMirostatEta())
				.penalizeNewline(fromOptions.getPenalizeNewline())
				.stop(fromOptions.getStop())
				.internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled())
                .build();
	}

	/**
	 * Convert the {@link OllamaOptions} object to a {@link Map} of key/value pairs.
	 * @return The {@link Map} of key/value pairs.
	 */
	public Map<String, Object> toMap() {
		return ModelOptionsUtils.objectToMap(this);
	}

	public OllamaOptions copy() {
		return fromOptions(this);
	}

    public static class Builder {

        private final OllamaOptions options = new OllamaOptions();

        public Builder model(String model) {
            this.options.model = model;
            return this;
        }

        public Builder format(Object format) {
            this.options.format = format;
            return this;
        }

        public Builder keepAlive(String keepAlive) {
            this.options.keepAlive = keepAlive;
            return this;
        }

        public Builder truncate(Boolean truncate) {
            this.options.truncate = truncate;
            return this;
        }

        public Builder useNUMA(Boolean useNUMA) {
            this.options.useNUMA = useNUMA;
            return this;
        }

        public Builder numCtx(Integer numCtx) {
            this.options.numCtx = numCtx;
            return this;
        }

        public Builder numBatch(Integer numBatch) {
            this.options.numBatch = numBatch;
            return this;
        }

        public Builder numGPU(Integer numGPU) {
            this.options.numGPU = numGPU;
            return this;
        }

        public Builder mainGPU(Integer mainGPU) {
            this.options.mainGPU = mainGPU;
            return this;
        }

        public Builder lowVRAM(Boolean lowVRAM) {
            this.options.lowVRAM = lowVRAM;
            return this;
        }

        public Builder f16KV(Boolean f16KV) {
            this.options.f16KV = f16KV;
            return this;
        }

        public Builder logitsAll(Boolean logitsAll) {
            this.options.logitsAll = logitsAll;
            return this;
        }

        public Builder vocabOnly(Boolean vocabOnly) {
            this.options.vocabOnly = vocabOnly;
            return this;
        }

        public Builder useMMap(Boolean useMMap) {
            this.options.useMMap = useMMap;
            return this;
        }

        public Builder useMLock(Boolean useMLock) {
            this.options.useMLock = useMLock;
            return this;
        }

        public Builder numThread(Integer numThread) {
            this.options.numThread = numThread;
            return this;
        }

        public Builder numKeep(Integer numKeep) {
            this.options.numKeep = numKeep;
            return this;
        }

        public Builder seed(Integer seed) {
            this.options.seed = seed;
            return this;
        }

        public Builder numPredict(Integer numPredict) {
            this.options.numPredict = numPredict;
            return this;
        }

        public Builder topK(Integer topK) {
            this.options.topK = topK;
            return this;
        }

        public Builder topP(Double topP) {
            this.options.topP = topP;
            return this;
        }

        public Builder minP(Double minP) {
            this.options.minP = minP;
            return this;
        }

        public Builder tfsZ(Float tfsZ) {
            this.options.tfsZ = tfsZ;
            return this;
        }

        public Builder typicalP(Float typicalP) {
            this.options.typicalP = typicalP;
            return this;
        }

        public Builder repeatLastN(Integer repeatLastN) {
            this.options.repeatLastN = repeatLastN;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.options.temperature = temperature;
            return this;
        }

        public Builder repeatPenalty(Double repeatPenalty) {
            this.options.repeatPenalty = repeatPenalty;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.options.presencePenalty = presencePenalty;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.options.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder mirostat(Integer mirostat) {
            this.options.mirostat = mirostat;
            return this;
        }

        public Builder mirostatTau(Float mirostatTau) {
            this.options.mirostatTau = mirostatTau;
            return this;
        }

        public Builder mirostatEta(Float mirostatEta) {
            this.options.mirostatEta = mirostatEta;
            return this;
        }

        public Builder penalizeNewline(Boolean penalizeNewline) {
            this.options.penalizeNewline = penalizeNewline;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.options.stop = stop;
            return this;
        }

        public Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
            this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
            return this;
        }

        public OllamaOptions build() {
            return this.options;
        }
    }

}
