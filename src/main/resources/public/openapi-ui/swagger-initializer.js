window.onload = function() {
  window.ui = SwaggerUIBundle({
    url: "../openapi.yaml",   // the API description lives one folder up, at /openapi.yaml
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl
    ],
    layout: "StandaloneLayout"
  });
};