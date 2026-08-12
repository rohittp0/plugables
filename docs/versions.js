(() => {
  const versionNodes = document.querySelectorAll("[data-plugin-version]");
  if (versionNodes.length === 0) return;

  const repositoryBase = "https://maven.rohittp.com/com/rohittp/plugables";
  const plugins = new Map();

  versionNodes.forEach((node) => {
    const plugin = node.dataset.pluginVersion;
    if (!plugins.has(plugin)) plugins.set(plugin, []);
    plugins.get(plugin).push(node);
  });

  plugins.forEach((nodes, plugin) => {
    const metadataUrl = `${repositoryBase}/${plugin}/maven-metadata.xml`;

    fetch(metadataUrl, { cache: "no-store" })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Metadata request failed with HTTP ${response.status}`);
        }
        return response.text();
      })
      .then((metadata) => {
        const xml = new DOMParser().parseFromString(metadata, "application/xml");
        if (xml.querySelector("parsererror")) {
          throw new Error("Metadata response is not valid XML");
        }

        const version = xml.querySelector("versioning > release")?.textContent?.trim();
        if (!version) {
          throw new Error("Metadata does not contain a release version");
        }

        nodes.forEach((node) => {
          node.textContent = version;
        });
      })
      .catch((error) => {
        console.error(`Unable to load the published ${plugin} version.`, error);
      });
  });
})();
