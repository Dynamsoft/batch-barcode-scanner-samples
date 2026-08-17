# Dynamsoft Batch Barcode Scanner Web Viewer

If you would like to get this demo project running, you can follow the steps below:

## Try the project

1. Set up

   ```cmd
   npm install
   ```

   or

   ```cmd
   yarn install
   ```

2. Configure the OneDrive client ID and redirect URI

   Open the environment file that matches your target:

   | File             | Purpose               |
   | ---------------- | --------------------- |
   | `.env.dev`       | Local development     |

   Set the following variables:

   ```dotenv
   DYNAMSOFT_ONEDRIVE_CLIENT_ID=<your-application-client-id>
   DYNAMSOFT_ONEDRIVE_REDIRECT_URI=<your-redirect-uri>
   ```

   - `DYNAMSOFT_ONEDRIVE_CLIENT_ID` — the Application (client) ID of your Azure app registration.
   - `DYNAMSOFT_ONEDRIVE_REDIRECT_URI` — the redirect URI registered in Azure AD, typically `http://localhost:5000/` for local development.

3. Run

   For development:

   ```cmd
   npm run dev
   ```

   or

   ```cmd
   yarn dev
   ```

   For production:

   ```cmd
   npm run build
   ```

   or

   ```cmd
   yarn build
   ```

## Contact Us

If you have any questions with these samples, feel free to submit an issue or [contact us](https://www.dynamsoft.com/company/contact/).
