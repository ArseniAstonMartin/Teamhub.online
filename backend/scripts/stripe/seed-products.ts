// TO-DO: Run this script against the live Stripe account per PRD §26.2, then copy the output
// into your .env and GitHub/Coolify secrets.
//
// Usage:
//   STRIPE_SECRET_KEY=rk_test_... npm run seed
//
// Pricing per PRD §17.1:
//   Free       $0/month
//   Starter    $49/month
//   Team       $169/month
//   Enterprise $349/month

import Stripe from "stripe";

const REQUIRED_ENV = "STRIPE_SECRET_KEY";

const PLANS: Array<{
  name: string;
  planKey: string;
  unitAmountCents: number;
}> = [
  { name: "Free", planKey: "free", unitAmountCents: 0 },
  { name: "Starter", planKey: "starter", unitAmountCents: 4900 },
  { name: "Team", planKey: "team", unitAmountCents: 16900 },
  { name: "Enterprise", planKey: "enterprise", unitAmountCents: 34900 },
];

async function main(): Promise<void> {
  const secretKey = process.env[REQUIRED_ENV];
  if (!secretKey) {
    console.error(
      `ERROR: ${REQUIRED_ENV} environment variable is not set.\n` +
        `Provision a Stripe restricted API key (rk_test_... for test, rk_live_... for production) ` +
        `and export it as ${REQUIRED_ENV}.`,
    );
    process.exit(1);
  }

  const stripe = new Stripe(secretKey, {
    apiVersion: "2026-04-22.dahlia" as Stripe.LatestApiVersion,
  });

  const priceIds: Record<string, string> = {};

  for (const plan of PLANS) {
    const productId = await ensureProduct(stripe, plan.name, plan.planKey);
    const priceId = await ensurePrice(
      stripe,
      productId,
      plan.planKey,
      plan.unitAmountCents,
    );
    priceIds[plan.planKey] = priceId;
  }

  console.log("\n# Copy these into your .env and GitHub/Coolify secrets:\n");
  console.log(`STRIPE_PRICE_FREE=${priceIds["free"]}`);
  console.log(`STRIPE_PRICE_STARTER=${priceIds["starter"]}`);
  console.log(`STRIPE_PRICE_TEAM=${priceIds["team"]}`);
  console.log(`STRIPE_PRICE_ENTERPRISE=${priceIds["enterprise"]}`);
}

async function ensureProduct(
  stripe: Stripe,
  name: string,
  planKey: string,
): Promise<string> {
  const existing = await stripe.products.list({
    limit: 10,
  });

  const found = existing.data.find(
    (p) => p.metadata?.teamhub_plan_key === planKey && p.active,
  );

  if (found) {
    console.log(`Product already exists — id=${found.id} name="${found.name}"`);
    return found.id;
  }

  const product = await stripe.products.create({
    name,
    metadata: { teamhub_plan_key: planKey },
  });

  console.log(`Created product — id=${product.id} name="${product.name}"`);
  return product.id;
}

async function ensurePrice(
  stripe: Stripe,
  productId: string,
  planKey: string,
  unitAmountCents: number,
): Promise<string> {
  const existing = await stripe.prices.list({
    product: productId,
    active: true,
    limit: 10,
  });

  const found = existing.data.find(
    (p) =>
      p.recurring?.interval === "month" &&
      p.unit_amount === unitAmountCents &&
      p.currency === "usd",
  );

  if (found) {
    console.log(
      `Price already exists — id=${found.id} plan=${planKey} amount=${unitAmountCents}¢/month`,
    );
    return found.id;
  }

  const price = await stripe.prices.create({
    product: productId,
    currency: "usd",
    unit_amount: unitAmountCents,
    recurring: { interval: "month" },
    metadata: { teamhub_plan_key: planKey },
  });

  console.log(
    `Created price — id=${price.id} plan=${planKey} amount=${unitAmountCents}¢/month`,
  );
  return price.id;
}

main().catch((err) => {
  console.error("Seed failed:", err);
  process.exit(1);
});
