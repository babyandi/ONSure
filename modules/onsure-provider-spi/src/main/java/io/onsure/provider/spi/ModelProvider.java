package io.onsure.provider.spi;

/** Transport-neutral candidate SPI for an explicitly configured external or local model provider. */
public interface ModelProvider extends AutoCloseable {
    ProviderDescriptor descriptor();

    ProviderHealth health() throws ProviderException;

    CompletionResponse complete(CompletionRequest request, ProviderContext context)
            throws ProviderException, InterruptedException;

    @Override
    default void close() throws Exception {}
}
