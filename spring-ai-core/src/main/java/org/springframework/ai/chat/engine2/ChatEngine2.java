package org.springframework.ai.chat.engine2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.ai.chat.prompt.Prompt;

public class ChatEngine2 implements Engine2 {

	private List<Retriever> retrievers;

	private List<Augmentor> augmentors;

	private Generator generator;

	private EngineListener engineListener;  //TODO

	public ChatEngine2(List<Retriever> retrievers, List<Augmentor> augmentors, Generator generator,
			EngineListener engineListener) {
		this.retrievers = retrievers;
		this.augmentors = augmentors;
		this.generator = generator;
		this.engineListener = engineListener;
	}

	@Override
	public EngineResponse2 call(EngineRequest2 engineRequest2) {
		RetrievalResponse retrievalResponse = doRetrieval(engineRequest2);

		AugmentRequest augmentRequest = new AugmentRequest(engineRequest2, retrievalResponse);
		AugmentResponse augmentResponse = doAugment(augmentRequest);

		GenerateRequest generateRequest = new GenerateRequest(engineRequest2, augmentResponse);
		GenerateResponse generateResponse = doGeneration(generateRequest);

		engineListener.onComplete(engineRequest2, generateResponse);

		return new EngineResponse2(engineRequest2, retrievalResponse, augmentResponse, generateResponse, List.of());
	}

	protected RetrievalResponse doRetrieval(EngineRequest2 engineRequest2) {
		RetrievalRequest retrievalRequest = new RetrievalRequest(engineRequest2);
		List<RetrievalResponse> retrievalResponses = new ArrayList<>();
		for (Retriever retriever : retrievers) {
			RetrievalResponse retrievalResponse = retriever.retrieve(retrievalRequest);
			retrievalResponses.add(retrievalResponse);
		}
		RetrievalResponse retrievalResponse = retrievalResponses.stream()
			.reduce(RetrievalResponse::merge)
			.orElse(new RetrievalResponse(Collections.emptyList(), Collections.emptyList()));
		return retrievalResponse;
	}

	protected AugmentResponse doAugment(AugmentRequest augmentRequest) {
		return executeChain(this.augmentors, augmentRequest);
		// return this.augmentor.augment(augmentRequest);
	}

	public AugmentResponse executeChain(List<Augmentor> augmentors, AugmentRequest initialRequest) {
		AugmentRequest currentRequest = initialRequest;
		AugmentResponse lastResponse = null;

		for (Augmentor augmentor : augmentors) {
			lastResponse = augmentor.augment(currentRequest);

			if (augmentors.indexOf(augmentor) < augmentors.size() - 1) {
				// Extract the prompt from the response for the next Augmentor
				Prompt newPrompt = lastResponse.getPrompt();

				String conversationId = currentRequest.getEngineRequest2().getConversationId();
				RetrievalResponse retrievalResponse = currentRequest.getRetrievalResponse(); // Or
																								// create/update
																								// as
																								// needed

				EngineRequest2 newEngineRequest = new EngineRequest2(conversationId, newPrompt);
				currentRequest = new AugmentRequest(newEngineRequest, retrievalResponse);
			}
		}

		// Return the last AugmentResponse from the final Augmentor in the list
		return lastResponse;
	}

	protected GenerateResponse doGeneration(GenerateRequest generateRequest) {
		return this.generator.generate(generateRequest);
	}

}
