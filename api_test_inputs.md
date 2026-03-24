# Unipay API — Test Inputs Reference

> **Base URL:** `http://localhost:8080`  
> **Auth Header:** `Authorization: Bearer <your_jwt_token>`

---

## Testing Sequence (Recommended Order)

```
1. Register Student
2. Login Student  → get JWT
3. Admin: Register Merchant
4. Admin: Login (as ADMIN) → get JWT
5. Admin: Register Card
6. Admin: Link Card to Student
7. Student: Get My Cards
8. Student: Recharge Wallet
9. Merchant Login → get JWT
10. Merchant: Process NFC Tap
11. Merchant: Refund
12. Check Transaction History
```

---

## 🔐 Auth APIs — `/api/auth`

### 1. Register Student
`POST /api/auth/register`

```json
{
  "name": "Umesh Shinde",
  "email": "umesh.student@unipay.com",
  "password": "Student@123",
  "studentId": "STU2024001"
}
```

### 2. Login Student
`POST /api/auth/login`

```json
{
  "email": "umesh.student@unipay.com",
  "password": "Student@123"
}
```

### 3. Merchant Login
`POST /api/auth/merchant/login`

```json
{
  "email": "canteen.merchant@unipay.com",
  "password": "Merchant@123"
}
```

### 4. Refresh Token
`POST /api/auth/refresh`

```json
{
  "refreshToken": "<refresh_token_from_login_response>"
}
```

### 5. Logout
`POST /api/auth/logout`  
🔒 Requires: Any valid JWT in `Authorization` header. No request body needed.

### 6. Get Current User (Me)
`GET /api/auth/me`  
🔒 Requires: Any valid JWT in `Authorization` header.

---

## 🛡️ Admin APIs — `/api/admin`

> 🔒 All endpoints require: **ADMIN JWT** in `Authorization` header.

### 7. Register a Merchant
`POST /api/admin/merchants/register`

```json
{
  "name": "Ravi Canteen",
  "email": "canteen.merchant@unipay.com",
  "password": "Merchant@123",
  "businessName": "Ravi's Campus Canteen"
}
```

### 8. List All Merchants
`GET /api/admin/merchants`  
No body needed.

### 9. List All Users (Paginated)
`GET /api/admin/users?page=0&size=10`

Optional filters:
```
GET /api/admin/users?role=STUDENT&page=0&size=5
GET /api/admin/users?role=MERCHANT&page=0&size=10
GET /api/admin/users?role=ADMIN&page=0&size=10
```
> Valid `role` values: `STUDENT`, `MERCHANT`, `ADMIN`

### 10. Get User Details by ID
`GET /api/admin/users/{id}`

```
GET /api/admin/users/1
GET /api/admin/users/2
```

### 11. Block a User
`PATCH /api/admin/users/{id}/block`

```json
{
  "reason": "Suspicious transaction activity detected."
}
```
> `reason` is optional — you can send an empty body `{}` or omit it.

### 12. Unblock a User
`PATCH /api/admin/users/{id}/unblock`  
No body needed.

---

## 💳 Card APIs (Admin) — `/api/admin/cards`

> 🔒 All endpoints require: **ADMIN JWT**.

### 13. Register a New Card
`POST /api/admin/cards`

```json
{
  "cardUid": "NFC-UID-A1B2C3"
}
```
> `cardUid` is the physical NFC/RFID chip UID (any unique string, e.g. hex value).

More sample UIDs:
```json
{ "cardUid": "04:AB:CD:EF:12:34" }
{ "cardUid": "RFID-CARD-001" }
{ "cardUid": "A3F9B21C" }
```

### 14. Link Card to a Student
`POST /api/admin/cards/link`

```json
{
  "cardUid": "NFC-UID-A1B2C3",
  "userId": 1
}
```

### 15. Unlink Card from User
`DELETE /api/admin/cards/{cardUid}/unlink`

```
DELETE /api/admin/cards/NFC-UID-A1B2C3/unlink
```
No body needed.

### 16. Activate a Card
`PATCH /api/admin/cards/{cardUid}/activate`

```
PATCH /api/admin/cards/NFC-UID-A1B2C3/activate
```
No body needed.

### 17. Deactivate a Card
`PATCH /api/admin/cards/{cardUid}/deactivate`

```
PATCH /api/admin/cards/NFC-UID-A1B2C3/deactivate
```
No body needed.

---

## 🃏 Student Card APIs — `/api/cards`

> 🔒 Requires: **STUDENT JWT**.

### 18. Get My Cards
`GET /api/cards/my-cards`  
No body needed.

### 19. Toggle My Card Status
`PATCH /api/cards/{cardUid}`

Activate:
```json
{
  "action": "activate",
  "reason": "Ready to use for campus payments"
}
```

Deactivate:
```json
{
  "action": "deactivate",
  "reason": "Card temporarily lost, deactivating for safety"
}
```
> Valid `action` values: `"activate"` or `"deactivate"` (case-insensitive).

---

## 💰 Wallet APIs — `/api/wallet`

> 🔒 Requires: **STUDENT JWT**.

### 20. Get Wallet Balance
`GET /api/wallet/balance`  
No body needed.

### 21. Create Recharge Order (Razorpay)
`POST /api/wallet/create-recharge-order`

```json
{
  "amount": 500
}
```

> Constraints: minimum **₹10**, maximum **₹10,000**.

More samples:
```json
{ "amount": 100 }
{ "amount": 1000 }
{ "amount": 10000 }
```

### 22. Verify Razorpay Payment
`POST /api/wallet/verify-payment`

```json
{
  "razorpayOrderId": "order_Pxyz1234567890",
  "razorpayPaymentId": "pay_Pabc1234567890",
  "razorpaySignature": "a5f3b1c2d4e8f7a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2"
}
```

> ⚠️ These values must come from the actual Razorpay checkout callback in production. Use Razorpay test credentials in test mode.

---

## 💸 Payment APIs — `/api/payment`

> 🔒 POST endpoints require: **MERCHANT JWT**.  
> 🔒 GET status can be called by any involved user.

### 23. Process NFC Card Tap (Merchant)
`POST /api/payment/tap`

```json
{
  "cardUid": "NFC-UID-A1B2C3",
  "amount": 150.00,
  "merchantId": 2
}
```

More samples:
```json
{
  "cardUid": "NFC-UID-A1B2C3",
  "amount": 1.00,
  "merchantId": 2
}
```
```json
{
  "cardUid": "A3F9B21C",
  "amount": 4999.99,
  "merchantId": 2
}
```

> Constraints: amount between **₹1.00** and **₹5,000.00**.

### 24. Get Payment Status
`GET /api/payment/status/{id}`

```
GET /api/payment/status/1
GET /api/payment/status/10
```

### 25. Process Refund (Merchant)
`POST /api/payment/refund`

```json
{
  "transactionId": 1,
  "amount": 150.00
}
```

Partial refund:
```json
{
  "transactionId": 1,
  "amount": 50.00
}
```

---

## 📋 Transaction APIs — `/api/transactions`

> 🔒 Requires: Any valid JWT (Student or Merchant).

### 26. Get Transaction History (Paginated)
`GET /api/transactions/history?page=0&size=10`

With filters:
```
GET /api/transactions/history?page=0&size=5&type=PAYMENT
GET /api/transactions/history?page=0&size=10&type=REFUND
GET /api/transactions/history?from=2024-01-01&to=2024-12-31
GET /api/transactions/history?type=PAYMENT&from=2024-04-01&to=2024-04-14&page=0&size=10
```

### 27. Get Transaction Detail
`GET /api/transactions/{id}`

```
GET /api/transactions/1
GET /api/transactions/5
```

---

## 👤 User Profile APIs — `/api/user/profile`

> 🔒 Requires: Any valid JWT.

### 28. Get My Profile
`GET /api/user/profile`  
No body needed.

### 29. Update My Profile
`PUT /api/user/profile`

```json
{
  "fullName": "Umesh Shinde",
  "phone": "9876543210"
}
```

Only fullName:
```json
{
  "fullName": "Umesh Shinde"
}
```

Only phone:
```json
{
  "phone": "9876543210"
}
```

> Constraints: `fullName` 2–100 chars; `phone` must be exactly **10 digits**.

---

## 🏪 Merchant Self-Service APIs — `/api/merchant`

> 🔒 Requires: **MERCHANT JWT**.

### 30. Get Merchant Profile
`GET /api/merchant/profile`  
No body needed.

### 31. Get Merchant Wallet Balance
`GET /api/merchant/balance`  
No body needed.

### 32. Get Merchant Transactions
`GET /api/merchant/transactions?page=0&size=10`

With filters:
```
GET /api/merchant/transactions?type=PAYMENT&page=0&size=5
GET /api/merchant/transactions?from=2024-04-01&to=2024-04-14
GET /api/merchant/transactions?type=REFUND&page=0&size=10
```

---

## ✅ Health Check

### 33. Health Check
`GET /actuator/health`  
or  
`GET /api/health`  
No auth needed.

---

## Validation Constraint Summary

| Field | Constraint |
|---|---|
| `password` | Min 8 characters |
| `email` | Valid email format |
| `phone` | Exactly 10 digits |
| `fullName` | 2–100 characters |
| `amount` (payment tap) | ₹1.00 – ₹5,000.00 |
| `amount` (wallet recharge) | ₹10 – ₹10,000 |
| `amount` (refund) | > ₹0.0001 |
| `cardUid` | Any non-blank string |
| `studentId` | Any non-blank string |

## ❌ Negative Test Cases

| Scenario | Expected Response |
|---|---|
| Login with wrong password | `401 Unauthorized` |
| Student hitting `/api/admin/*` | `403 Forbidden` |
| Merchant hitting student-only `/api/cards/*` | `403 Forbidden` |
| Payment tap with amount > ₹5000 | `400 Bad Request` |
| Wallet recharge with amount < ₹10 | `400 Bad Request` |
| Toggle card with `action: "invalid"` | `400 Bad Request / BusinessException` |
| Block already-blocked user | Graceful or idempotent |
| Refund already-refunded transaction | `400 Bad Request` |
| Access blocked user's protected endpoints | `403 / 401` |
