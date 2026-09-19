package com.srideep.pocketforge.workspace

/**
 * Starter files for a new project. Deliberately plain HTML/CSS/JS: the on-device dev
 * server is a static server, so a project is runnable the moment it is written, with no
 * install step and no network.
 */
object ProjectTemplates {

    fun starter(projectName: String): Map<String, String> = mapOf(
        "index.html" to """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>$projectName</title>
                <link rel="stylesheet" href="styles.css" />
              </head>
              <body>
                <main>
                  <h1>$projectName</h1>
                  <p>Edit the files in this project and the preview reloads itself.</p>
                </main>
                <script src="main.js"></script>
              </body>
            </html>
        """.trimIndent(),

        "styles.css" to """
            :root {
              color-scheme: light dark;
              --bg: #0f1115;
              --fg: #e7e9ee;
              --accent: #6ea8fe;
            }

            body {
              margin: 0;
              min-height: 100vh;
              display: grid;
              place-items: center;
              font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
              background: var(--bg);
              color: var(--fg);
            }

            h1 {
              margin: 0 0 0.5rem;
              color: var(--accent);
            }
        """.trimIndent(),

        "main.js" to """
            console.log('$projectName is running');
        """.trimIndent(),
    )
}
