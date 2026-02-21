# BiasharaHub Documentation

## System documentation

The main system document is **`BiasharaHub_System_Documentation.md`**. It covers:

- Executive summary and architecture
- Technology stack and multi-tenancy
- User roles and authentication (web + WhatsApp)
- Core domains (products, orders, payments, shipments, services, disputes, etc.)
- Integrations (M-Pesa, WhatsApp, email, R2, Google Meet, Kafka)
- WhatsApp chatbot (customer and seller/provider flows)
- API reference summary
- Security, configuration, database, and PDF generation

## Generating the PDF

### 1. Using Pandoc (recommended)

1. Install [Pandoc](https://pandoc.org/) (e.g. `choco install pandoc` on Windows).
2. From the `docs` folder run:
   ```bash
   pandoc BiasharaHub_System_Documentation.md -o BiasharaHub_System_Documentation.pdf -V geometry:margin=1in
   ```
   Or run the script:
   ```powershell
   .\generate-pdf.ps1
   ```

### 2. Using VS Code

1. Install the **Markdown PDF** extension (e.g. by yzane).
2. Open `BiasharaHub_System_Documentation.md`.
3. Right-click → **Markdown PDF: Export (pdf)**.
4. The PDF is created in the same folder.

### 3. Using a browser

1. Open the `.md` file in a Markdown viewer or paste content into a site like [Markdown to PDF](https://www.markdowntopdf.com/).
2. Use “Print to PDF” or the site’s export to save as PDF.

The generated PDF should be named **`BiasharaHub_System_Documentation.pdf`** and placed in the `docs` folder (when using Pandoc or VS Code).
