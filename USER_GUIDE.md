# PrintKeep End-User Guide (v1.0 GA)

This document describes how the print quota enforcement system works, what happens during quota rejections, and how to verify balances.

---

## 1. How Printing Works

When you submit a document to an office printer:
1. **Request Interception**: Your print job stream is analyzed by PrintKeep before reaching the printer.
2. **Identity Resolution**: The system extracts your Windows domain username from your workspace credentials.
3. **Quota Check**: The system calculates the page count (including page/duplex multipliers) and checks your remaining monthly quota.
4. **Delivery**: If you have sufficient pages, the job starts printing immediately.

---

## 2. Quotas Allocation & Monthly Reset

- **Allocation**: Every employee is allocated a monthly default quota (usually `100 pages` unless adjusted by your department head).
- **Reset Day**: On the first day of every month at midnight, your used page count resets to zero, and your page balance refreshes. Unused pages do not roll over to the next month.
- **Double-sided printing (Duplex)**: Standard prints count as 1 page per sheet face. Double-sided printing is recommended to conserve paper, but each printed side counts toward your quota limit.

---

## 3. Rejected Print Jobs

If your job is rejected:
- **Reason**: You have exceeded your allocated monthly page limit, or your account is marked inactive in Active Directory.
- **Notification**: Your workstation print queue will show an error message like "Hold for Authentication" or "Out of Quota" (status code `0x0401` returned in IPP format).
- **Remediation**: Contact your department manager to request a temporary quota adjustment.

---

## 4. Frequently Asked Questions (FAQ)

#### Q: How can I check my remaining page quota?
A: You can check your balance by logging into your intranet dashboard or contacting the IT Helpdesk.

#### Q: My print job failed but my quota was still deducted. What should I do?
A: If a physical paper jam occurs after the print job has been authorized by the proxy, the pages have already been recorded. Open a support ticket to have your balance adjusted.

#### Q: Are large PDF documents supported?
A: Yes. Large documents are streamed in small chunks to prevent network print dropouts.
