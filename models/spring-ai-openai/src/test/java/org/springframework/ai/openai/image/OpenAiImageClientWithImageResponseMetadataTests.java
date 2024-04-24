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
package org.springframework.ai.openai.image;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.openai.OpenAiImageClient;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @author John Blum
 * @author Christian Tzolov
 * @since 0.7.0
 */
@RestClientTest(OpenAiImageClientWithImageResponseMetadataTests.Config.class)
public class OpenAiImageClientWithImageResponseMetadataTests {

	private static String testApiKey = "sk-1234567890";

	@Autowired
	private OpenAiImageClient openAiImageClient;

	@Autowired
	private MockRestServiceServer server;

	@AfterEach
	void resetMockServer() {
		server.reset();
	}

	@Test
	void aiResponseContainsImageResponseMetadata() {

		prepareMock();

		ImagePrompt prompt = new ImagePrompt("Create an image of a mini golden doodle dog.");

		ImageResponse response = this.openAiImageClient.call(prompt);

		assertThat(response).isNotNull();
		List<ImageGeneration> imageGenerations = response.getResults();
		assertThat(imageGenerations).isNotNull();
		assertThat(imageGenerations).hasSize(2);

		ImageResponseMetadata imageResponseMetadata = response.getMetadata();

		assertThat(imageResponseMetadata).isNotNull();

		Long created = imageResponseMetadata.created();

		assertThat(created).isNotNull();
		assertThat(created).isEqualTo(1589478378);

		ImageResponseMetadata responseMetadata = response.getMetadata();

		assertThat(responseMetadata).isNotNull();

	}

	private void prepareMock() {

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_LIMIT_HEADER.getName(), "4000");
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_REMAINING_HEADER.getName(), "999");
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_RESET_HEADER.getName(), "2d16h15m29s");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_LIMIT_HEADER.getName(), "725000");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_REMAINING_HEADER.getName(), "112358");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_RESET_HEADER.getName(), "27h55s451ms");

		server.expect(requestTo("v1/images/generations"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + testApiKey))
			.andRespond(withSuccess(getJson(), MediaType.APPLICATION_JSON).headers(httpHeaders));

	}

	private String getJson() {
		return """
					{
					  "created": 1589478378,
					  "data": [
						{
						  "url": "https://upload.wikimedia.org/wikipedia/commons/4/4e/Mini_Golden_Doodle.jpg"
						},
						{
						  "url": "https://upload.wikimedia.org/wikipedia/commons/8/85/Goldendoodle_puppy_Marty.jpg"
						}
					  ]
					}
				""";
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiImageApi imageGenerationApi(RestClient.Builder builder) {
			return new OpenAiImageApi("", testApiKey, builder);
		}

		@Bean
		public OpenAiImageClient openAiImageClient(OpenAiImageApi openAiImageApi) {
			return new OpenAiImageClient(openAiImageApi);
		}

	}

}
