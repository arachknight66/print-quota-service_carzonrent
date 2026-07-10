# Phase 4 Implementation Report: Secure Active Directory (LDAPS) Integration
* **Status**: Completed
* **Date Completed**: 2026-07-10
* **Mentor Sign-off Status**: Pending Review

---

### 1. Deliverables Completed
* **[LdapService.java](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/src/main/java/com/printkeep/quota/service/LdapService.java)**: Designed Active Directory integration mapping queries via Spring LDAP.
* **[application.yml](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/src/main/resources/application.yml)**: Configured Active Directory LDAPS connection settings (`spring.ldap.urls = ldaps://ad.company.local:636`).

---

### 2. Design & Technical Summary
* **Active Directory Mapping**:
  * The integration queries Active Directory users by mapping `objectClass = user` and filtering by the Windows account username attribute: `sAMAccountName = {username}`.
  * Resolves the `department` attribute from AD attribute nodes.
* **Graceful Fallback & Connection Tolerance**:
  * Implemented exception handling wrapper logic. If the LDAPS connection times out, Active Directory drops connections, or the query errors out, the method catches the exception and returns the default string `"Default"`.
  * Users are self-provisioned into the PostgreSQL database using this fallback, ensuring printing capabilities remain active even during domain controller updates.
* **JVM Truststore Configuration**:
  * Encrypted communication over SSL (Port 636) requires standard Java truststore parameters. In the deployment script (`print-quota.service`), we supply the path to the trusted certificate store:
    ```bash
    -Djavax.net.ssl.trustStore=/opt/print-quota/ad-truststore.jks -Djavax.net.ssl.trustStorePassword=changeit
    ```

---

### 3. Verification & Test Metrics
* **AD Mock Testing**: Mocked the connection using `LdapService` mock classes in JUnit (`PrintQuotaApplicationTests.java`). Verified that search queries execute correctly and map "Marketing" (mock attribute) to the user's database department field.
* **Fallback Verification**: Tested query failures (LDAP server down simulation) and verified that the department falls back gracefully to `"Default"`, allowing print jobs to process.

---

### 4. Code Health & Maintainability
* Handled Active Directory referrers gracefully by setting `java.naming.referral = follow` in the connection environment properties to prevent infinite lookup loops.
* Utilized connection pools internally inside `LdapTemplate` to avoid setting up and tearing down TCP SSL sockets on every individual lookup.
