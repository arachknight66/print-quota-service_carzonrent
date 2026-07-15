# Client Certificate Issuance Guide

**Audience**: IT/PKI administrators responsible for managing print-system mTLS client certificates.  
**System**: Print Quota Management System — `print-quota-core`  
**Last Updated**: 2026-07

---

## Overview

Every employee workstation that submits print jobs to the Print Quota Management System must present a valid X.509 client certificate during the TLS handshake. The server is configured with `server.ssl.client-auth: need` (mandatory), so connections without a valid, trusted client certificate are dropped at the TLS layer before reaching any application code.

The client certificate Common Name (CN) **must exactly match** (case-insensitively) the employee's `domain_username` as stored in Active Directory and synced into the print-quota database. The `IdentityResolutionStage` enforces this binding on every print request.

---

## 1. Prerequisites

- Active Directory Certificate Services (AD CS) is available on the domain.
- A subordinate or dedicated **Print Workstation Client CA** certificate template is configured in AD CS (separate from the server TLS template to allow independent revocation).
- The CA certificate that signed client certs is installed in the print server's truststore (`truststore.p12`).
- Operator has `Certificate Manager` rights in AD CS.

---

## 2. CN Format

The certificate Common Name **must** be the employee's Active Directory `sAMAccountName` (the short domain username, without the domain prefix):

```
CN = <sAMAccountName>
```

**Examples**:

| Employee | AD sAMAccountName | Certificate CN |
|---|---|---|
| John Doe | `jdoe` | `CN=jdoe` |
| Alice Smith | `asmith` | `CN=asmith` |
| Bob Kumar | `bkumar` | `CN=bkumar` |

> **IMPORTANT**: Do NOT include the domain prefix (`COMPANY\`) in the CN. The quota system strips domain prefixes during LDAP sync and stores only the bare `sAMAccountName` in `domain_username`. Mismatches will cause every print job from that workstation to be rejected with IPP error `0x0401`.

---

## 3. Issuing a Client Certificate (AD CS — certreq)

### Command-Line via `certreq` (Recommended for scripted bulk issuance)

**Step 1** — Create a request policy file (`print-client-req.inf`) for the employee:

```ini
[Version]
Signature = "$Windows NT$"

[NewRequest]
Subject     = "CN=jdoe,OU=Workstations,DC=company,DC=local"
KeySpec     = AT_KEYEXCHANGE
KeyLength   = 2048
Exportable  = FALSE
MachineKeySet = FALSE
SMIME       = FALSE
PrivateKeyArchive = FALSE
UserProtected = FALSE
UseExistingKeySet = FALSE
ProviderName = "Microsoft RSA SChannel Cryptographic Provider"
ProviderType = 12
RequestType  = PKCS10
KeyUsage     = 0xa0
ValidityPeriod    = Years
ValidityPeriodUnits = 2

[Extensions]
; Client Authentication OID (1.3.6.1.5.5.7.3.2)
2.5.29.37 = "{text}1.3.6.1.5.5.7.3.2"

[RequestAttributes]
CertificateTemplate = PrintWorkstationClient
```

Replace `CN=jdoe` with the actual employee sAMAccountName.

**Step 2** — Generate and submit the request:

```cmd
:: Generate the CSR
certreq -new print-client-req.inf jdoe-client.csr

:: Submit to the CA (replace CA_HOSTNAME\CA_NAME accordingly)
certreq -submit -config "CA_HOSTNAME\CA_NAME" jdoe-client.csr jdoe-client.cer

:: Accept and install the issued certificate
certreq -accept jdoe-client.cer
```

**Step 3** — Export the certificate with private key to PKCS#12 for deployment to the workstation:

```cmd
:: Export to PFX (prompt will ask for export password)
certutil -exportpfx -user My jdoe jdoe-client.pfx
```

**Step 4** — Deploy `jdoe-client.pfx` to the workstation's print client credential store.

---

### Option B: AD CS Web Enrollment (`certsrv`)

1. Open `https://ca.company.local/certsrv` on the target workstation.
2. Select **Request a certificate** → **Advanced certificate request**.
3. Choose the **PrintWorkstationClient** template.
4. Set the Subject Name to `CN=<sAMAccountName>` (e.g., `CN=jdoe`).
5. Submit and download the issued certificate, then import it into the workstation certificate store.

---

## 4. Adding the CA Certificate to the Print Server Truststore

The print server must trust the CA that signed client certificates. Run this on the print server whenever a new issuing CA is added:

```bash
keytool -import \
  -alias print-workstation-client-ca \
  -file print-workstation-client-ca.cer \
  -keystore /etc/printkeep/truststore.p12 \
  -storetype PKCS12 \
  -storepass "${TRUSTSTORE_PASSWORD}"
```

Set the `TRUSTSTORE_PATH` environment variable to point to this file and restart the service.

---

## 5. Renewal Cadence

| Parameter | Value |
|---|---|
| **Certificate validity** | 2 years |
| **Renewal trigger** | 60 days before expiry |
| **Renewal method** | Issue a new certificate; old cert may coexist until its expiry date |
| **Reminder** | Configure AD CS expiry alert or a calendar reminder per batch |

Tip: Use an AD CS enrollment policy and auto-enrollment GPO to automate renewal for domain-joined workstations.

---

## 6. Revoking a Certificate on Offboarding

When an employee leaves the organisation, their client certificate **must be revoked immediately**.

**Step 1** — Revoke in AD CS via MMC:

1. Open **Certification Authority** > **Issued Certificates**.
2. Find the certificate (filter by Subject CN = sAMAccountName).
3. Right-click > **All Tasks** > **Revoke Certificate**.
4. Select reason: **Key Compromise** (if device is lost) or **Cessation of Operation** (normal offboarding).

Or via command line:

```cmd
:: Revoke by serial number (find in AD CS MMC or via certutil -view)
certutil -revoke <SERIAL_NUMBER> 0
:: Reason codes: 0=Unspecified, 1=Key Compromise, 5=Cessation of Operation
```

**Step 2** — Publish an updated CRL immediately:

```cmd
certutil -CRL
```

**Step 3** — Verify the JVM CRL/OCSP checking is enabled on the print server:

```bash
# Add to JVM startup flags in the service unit file:
-Dcom.sun.security.enableCRLDP=true -Dcom.sun.net.ssl.checkRevocation=true
```

**Step 4** — Disable the user account in Active Directory. The LDAP sync (every 15 minutes in production) will update `is_active = false` in the database within one sync cycle, providing a second blocking layer.

---

## 7. Audit Trail

On a CN mismatch (cert CN does not case-insensitively match `requesting-user-name`), the system logs at WARN level:

```
[CorrID: <id>] Client certificate identity mismatch. certificateCN=mallory, claimedUsername=jdoe, sourceIp=192.168.1.100, timestamp=...
```

Security operations should alert on this log pattern as it indicates a possible spoofing attempt or misconfigured workstation certificate.

---

## 8. Quick-Reference Summary

```
One-time setup:
  1. Create "PrintWorkstationClient" template in AD CS
  2. Import issuing CA cert into /etc/printkeep/truststore.p12

Per-employee onboarding:
  1. Run certreq with CN = sAMAccountName (no domain prefix)
  2. Deploy .pfx to workstation print client
  3. Smoke-test: send a print job and verify it is allowed

Per-employee offboarding:
  1. Revoke cert in AD CS MMC -> Publish CRL immediately
  2. Disable AD account (LDAP sync disables quota system within 15 min)
```
