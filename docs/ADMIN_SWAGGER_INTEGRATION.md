# Admin screen: Showing Swagger API documentation

Only **SUPER_ADMIN** and **ASSISTANT_ADMIN** can access API documentation. The backend exposes:

1. **Swagger UI** and **OpenAPI spec** – restricted to admin roles.
2. **Admin endpoint** – returns the docs URLs so the admin screen can open or embed them.

## Backend

- **GET /api/admin/api-docs-info** (requires admin JWT)  
  Returns:
  ```json
  {
    "swaggerUiUrl": "http://localhost:5050/api/swagger-ui.html",
    "openApiSpecUrl": "http://localhost:5050/api/v3/api-docs"
  }
  ```
- **GET /api/swagger-ui.html** and **GET /api/v3/api-docs** – require admin JWT (same roles).

## Option A: Link that opens Swagger in a new tab

Because the new tab does not send your app’s JWT, opening `swaggerUiUrl` in a new tab will get **401/403** unless the user logs in again on the backend. So a plain “Open API docs” link is only useful if you later add backend support for e.g. a short-lived cookie set after admin login.

For now, use **Option B** so the user stays in the admin app and uses their existing token.

## Option B: Embed Swagger UI on the admin page (recommended)

1. Call the API from the admin screen (with the admin’s JWT):

   ```http
   GET /api/admin/api-docs-info
   Authorization: Bearer <access_token>
   ```

2. Use the **OpenAPI spec URL** from the response and fetch the spec with the same token:

   ```http
   GET /api/v3/api-docs
   Authorization: Bearer <access_token>
   ```

3. Render Swagger UI in your admin app using the fetched spec and pass the JWT for “Try it out”:
   - **React:** e.g. `swagger-ui-react`; pass the spec (object or URL) and `persistAuthorization: true`; set auth with your token so requests use it.
   - **Vue/Angular:** use the same idea: load the spec (with auth), render Swagger UI, and configure the security scheme with the current Bearer token.

Example (React with `swagger-ui-react`):

```jsx
import SwaggerUI from 'swagger-ui-react';
import 'swagger-ui-react/swagger-ui.css';

// In your Admin API Docs page component:
const [spec, setSpec] = useState(null);
const token = getAccessToken(); // from your auth context

useEffect(() => {
  fetch(`${API_BASE}/admin/api-docs-info`, { headers: { Authorization: `Bearer ${token}` } })
    .then(res => res.json())
    .then(({ openApiSpecUrl }) =>
      fetch(openApiSpecUrl, { headers: { Authorization: `Bearer ${token}` } }).then(r => r.json())
    )
    .then(setSpec);
}, [token]);

if (!spec) return <div>Loading API docs...</div>;

return (
  <SwaggerUI
    spec={spec}
    persistAuthorization
    requestInterceptor={(req) => {
      if (token) req.headers.Authorization = `Bearer ${token}`;
      return req;
    }}
  />
);
```

This way admins view the full Swagger documentation on the admin screen and “Try it out” uses their existing session.

## Summary

- **Backend:** Swagger and `/api/admin/api-docs-info` are restricted to **SUPER_ADMIN** and **ASSISTANT_ADMIN**.
- **Admin screen:** Call **GET /api/admin/api-docs-info** with the admin JWT, then either open the returned URLs (with the caveat above for a new tab) or, preferably, fetch the OpenAPI spec and embed Swagger UI as in Option B.
