# ONSure Provider SPI candidate

This isolated Maven module defines the minimum transport-neutral boundary for a future real model provider. It contains no credentials, provider SDK, network implementation, service loader registration, or dependency on ONSure core. Provider implementations remain opt-in and must enforce their own egress, secret, cost, retry, and evidence policies.

The package remains `io.onsure.provider.spi`; the future namespace candidate is recorded only as `kr.co.oruda.products.onsure` and is not applied here. This is a compatibility candidate, not release authority or final assurance.
