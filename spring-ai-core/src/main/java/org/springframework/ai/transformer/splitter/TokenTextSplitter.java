/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.transformer.splitter;

import java.util.ArrayList;
import java.util.List;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

import org.springframework.util.Assert;

/**
 * @author Raphael Yu
 * @author Christian Tzolov
 */
public class TokenTextSplitter extends TextSplitter {

	private final EncodingRegistry registry = Encodings.newLazyEncodingRegistry();

	private final Encoding encoding = registry.getEncoding(EncodingType.CL100K_BASE);

	// The target size of each text chunk in tokens
	private int defaultChunkSize = 800;

	// The minimum size of each text chunk in characters
	private int minChunkSizeChars = 350;

	// Discard chunks shorter than this
	private int minChunkLengthToEmbed = 5;

	// The maximum number of chunks to generate from a text
	private int maxNumChunks = 10000;

	private boolean keepSeparator = true;

	public TokenTextSplitter() {
	}

	public TokenTextSplitter(boolean keepSeparator) {
		this.keepSeparator = keepSeparator;
	}

	public TokenTextSplitter(int defaultChunkSize, int minChunkSizeChars, int minChunkLengthToEmbed, int maxNumChunks,
			boolean keepSeparator) {
		this.defaultChunkSize = defaultChunkSize;
		this.minChunkSizeChars = minChunkSizeChars;
		this.minChunkLengthToEmbed = minChunkLengthToEmbed;
		this.maxNumChunks = maxNumChunks;
		this.keepSeparator = keepSeparator;
	}

	@Override
	protected List<String> splitText(String text) {
		return split(text, this.defaultChunkSize);
	}

	public List<String> split(String text, int chunkSize) {
		if (text == null || text.trim().isEmpty()) {
			return new ArrayList<>();
		}

		List<Integer> tokens = getEncodedTokens(text);
		List<String> chunks = new ArrayList<>();
		int numChunks = 0;
		while (!tokens.isEmpty() && numChunks < this.maxNumChunks) {
			List<Integer> chunk = tokens.subList(0, Math.min(chunkSize, tokens.size()));
			String chunkText = decodeTokens(chunk);

			// Skip the chunk if it is empty or whitespace
			if (chunkText.trim().isEmpty()) {
				tokens = tokens.subList(chunk.size(), tokens.size());
				continue;
			}

			// Find the last period or punctuation mark in the chunk
			int lastPunctuation = Math.max(chunkText.lastIndexOf('.'), Math.max(chunkText.lastIndexOf('?'),
					Math.max(chunkText.lastIndexOf('!'), chunkText.lastIndexOf('\n'))));

			if (lastPunctuation != -1 && lastPunctuation > this.minChunkSizeChars) {
				// Truncate the chunk text at the punctuation mark
				chunkText = chunkText.substring(0, lastPunctuation + 1);
			}

			String chunkTextToAppend = this.keepSeparator ? chunkText.trim()
					: chunkText.replace(System.lineSeparator(), " ").trim();
			if (chunkTextToAppend.length() > this.minChunkLengthToEmbed) {
				chunks.add(chunkTextToAppend);
			}

			// Remove the tokens corresponding to the chunk text from the remaining tokens
			tokens = tokens.subList(getEncodedTokens(chunkText).size(), tokens.size());

			numChunks++;
		}

		// Handle the remaining tokens
		if (!tokens.isEmpty()) {
			String remainingText = decodeTokens(tokens).replace(System.lineSeparator(), " ").trim();
			if (remainingText.length() > this.minChunkLengthToEmbed) {
				chunks.add(remainingText);
			}
		}

		return chunks;
	}

	private List<Integer> getEncodedTokens(String text) {
		Assert.notNull(text, "Text must not be null");
		return this.encoding.encode(text).boxed();
	}

	private String decodeTokens(List<Integer> tokens) {
		Assert.notNull(tokens, "Tokens must not be null");
		var tokensIntArray = new IntArrayList(tokens.size());
		tokens.forEach(tokensIntArray::add);
		return this.encoding.decode(tokensIntArray);
	}

}
