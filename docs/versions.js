(() => {
  const versionNodes = document.querySelectorAll("[data-plugin-version]");
  if (versionNodes.length === 0) return;

  const versionsUrl = new URL("versions.json", document.baseURI);
  versionsUrl.searchParams.set("_", Date.now().toString());

  fetch(versionsUrl, { cache: "no-store" })
    .then((response) => {
      if (!response.ok) {
        throw new Error(`Version manifest request failed with HTTP ${response.status}`);
      }
      return response.json();
    })
    .then((versions) => {
      versionNodes.forEach((node) => {
        const plugin = node.dataset.pluginVersion;
        const version = versions[plugin];
        if (typeof version === "string" && version.length > 0) {
          node.textContent = version;
        }
      });
    })
    .catch((error) => {
      console.error("Unable to load published Plugables versions.", error);
    });
})();
