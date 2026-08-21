import DefaultTheme from "vitepress/theme"

export default {
  extends: DefaultTheme,
  enhanceApp({ app, router }) {
    if (typeof window !== "undefined") {
      document.addEventListener("click", (e) => {
        const link = e.target.closest("a");
        if (link) {
          const href = link.getAttribute("href") || "";
          if (href.includes("to-platform-console") || href.includes("platform/console")) {
            e.preventDefault();
            e.stopPropagation();
            window.location.href = "/platform/console/dashboard";
          }
        }
      }, true);
    }
  }
};
