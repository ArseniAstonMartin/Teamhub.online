(() => {
  document.documentElement.classList.add("js");

  const toggle = document.querySelector("[data-nav-toggle]");
  const drawer = document.querySelector("[data-nav-drawer]");
  if (toggle && drawer) {
    toggle.addEventListener("click", () => {
      const open = drawer.classList.toggle("is-open");
      toggle.setAttribute("aria-expanded", String(open));
    });
  }

  const prefersReduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const reveals = Array.from(document.querySelectorAll(".reveal"));

  const markInView = () => {
    reveals.forEach((el) => {
      const rect = el.getBoundingClientRect();
      if (rect.top < window.innerHeight * 0.92 && rect.bottom > 0) {
        el.classList.add("is-visible");
      }
    });
  };

  if (prefersReduced || !("IntersectionObserver" in window)) {
    reveals.forEach((el) => el.classList.add("is-visible"));
  } else {
    markInView();
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            io.unobserve(entry.target);
          }
        });
      },
      { rootMargin: "0px 0px -4% 0px", threshold: 0.05 }
    );
    reveals.forEach((el) => {
      if (!el.classList.contains("is-visible")) io.observe(el);
    });
  }

  const slider = document.querySelector("[data-jobs-slider]");
  if (!slider) return;

  const readout = document.querySelector("[data-jobs-readout]");
  const planName = document.querySelector("[data-recommended-plan]");
  const planBlurb = document.querySelector("[data-recommended-blurb]");
  const plans = document.querySelectorAll("[data-plan]");

  const tiers = [
    {
      id: "free",
      max: 25,
      name: "Free",
      blurb: "Good for trying the workflow with a tiny shop.",
    },
    {
      id: "starter",
      max: 250,
      name: "Starter",
      blurb: "Fits most small crews that are past the “just us two” stage.",
    },
    {
      id: "team",
      max: 1500,
      name: "Team",
      blurb: "Built for busy shops running a real dispatch board every day.",
    },
    {
      id: "enterprise",
      max: Infinity,
      name: "Enterprise",
      blurb: "Unlimited jobs and seats when you’re past the usual caps.",
    },
  ];

  const formatJobs = (value) => {
    const n = Number(value);
    if (n >= 2000) return "2,000+";
    return n.toLocaleString("en-US");
  };

  const pickTier = (jobs) => tiers.find((t) => jobs <= t.max) || tiers[tiers.length - 1];

  const sync = () => {
    const jobs = Number(slider.value);
    if (readout) readout.textContent = `${formatJobs(jobs)} jobs / month`;
    const tier = pickTier(jobs);
    if (planName) planName.textContent = tier.name;
    if (planBlurb) planBlurb.textContent = tier.blurb;
    plans.forEach((card) => {
      const active = card.getAttribute("data-plan") === tier.id;
      card.classList.toggle("is-recommended", active);
    });
  };

  slider.addEventListener("input", sync);
  sync();
})();

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch(() => {});
  });
}
