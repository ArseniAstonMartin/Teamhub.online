package com.teamhub.billing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Verifies that {@link StripePriceProperties} causes a startup failure when any required value is
 * blank (TASK-060 test step 3, PRD §17.1).
 *
 * <p>Uses {@link ApplicationContextRunner} so the test does not spin up the full Spring context
 * (avoiding the need for real external services such as Postgres, Redis, or Stripe).
 */
class StripePricePropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(LocalValidatorFactoryBean.class)
          .withUserConfiguration(StripeConfig.class);

  @Test
  void contextLoads_whenAllPropertiesProvided() {
    contextRunner
        .withPropertyValues(
            "teamhub.stripe.secret-key=rk_test_dummy",
            "teamhub.stripe.price-free=price_free",
            "teamhub.stripe.price-starter=price_starter",
            "teamhub.stripe.price-team=price_team",
            "teamhub.stripe.price-enterprise=price_enterprise",
            "teamhub.stripe.webhook-secret=whsec_dummy")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              StripePriceProperties props = ctx.getBean(StripePriceProperties.class);
              assertThat(props.secretKey()).isEqualTo("rk_test_dummy");
              assertThat(props.priceFree()).isEqualTo("price_free");
              assertThat(props.priceStarter()).isEqualTo("price_starter");
              assertThat(props.priceTeam()).isEqualTo("price_team");
              assertThat(props.priceEnterprise()).isEqualTo("price_enterprise");
              assertThat(props.webhookSecret()).isEqualTo("whsec_dummy");
            });
  }

  @Test
  void contextFails_whenSecretKeyIsBlank() {
    contextRunner
        .withPropertyValues(
            "teamhub.stripe.secret-key=",
            "teamhub.stripe.price-free=price_free",
            "teamhub.stripe.price-starter=price_starter",
            "teamhub.stripe.price-team=price_team",
            "teamhub.stripe.price-enterprise=price_enterprise",
            "teamhub.stripe.webhook-secret=whsec_dummy")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void contextFails_whenPriceFreeIsBlank() {
    contextRunner
        .withPropertyValues(
            "teamhub.stripe.secret-key=rk_test_dummy",
            "teamhub.stripe.price-free=",
            "teamhub.stripe.price-starter=price_starter",
            "teamhub.stripe.price-team=price_team",
            "teamhub.stripe.price-enterprise=price_enterprise",
            "teamhub.stripe.webhook-secret=whsec_dummy")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void contextFails_whenPriceStarterIsBlank() {
    contextRunner
        .withPropertyValues(
            "teamhub.stripe.secret-key=rk_test_dummy",
            "teamhub.stripe.price-free=price_free",
            "teamhub.stripe.price-starter=",
            "teamhub.stripe.price-team=price_team",
            "teamhub.stripe.price-enterprise=price_enterprise",
            "teamhub.stripe.webhook-secret=whsec_dummy")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void contextFails_whenPriceTeamIsBlank() {
    contextRunner
        .withPropertyValues(
            "teamhub.stripe.secret-key=rk_test_dummy",
            "teamhub.stripe.price-free=price_free",
            "teamhub.stripe.price-starter=price_starter",
            "teamhub.stripe.price-team=",
            "teamhub.stripe.price-enterprise=price_enterprise",
            "teamhub.stripe.webhook-secret=whsec_dummy")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void contextFails_whenPriceEnterpriseIsBlank() {
    contextRunner
        .withPropertyValues(
            "teamhub.stripe.secret-key=rk_test_dummy",
            "teamhub.stripe.price-free=price_free",
            "teamhub.stripe.price-starter=price_starter",
            "teamhub.stripe.price-team=price_team",
            "teamhub.stripe.price-enterprise=",
            "teamhub.stripe.webhook-secret=whsec_dummy")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void contextFails_whenWebhookSecretIsBlank() {
    contextRunner
        .withPropertyValues(
            "teamhub.stripe.secret-key=rk_test_dummy",
            "teamhub.stripe.price-free=price_free",
            "teamhub.stripe.price-starter=price_starter",
            "teamhub.stripe.price-team=price_team",
            "teamhub.stripe.price-enterprise=price_enterprise",
            "teamhub.stripe.webhook-secret=")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void contextFails_whenAllPropertiesMissing() {
    contextRunner.run(ctx -> assertThat(ctx).hasFailed());
  }
}
