<div align="center">

<img src="frontend/public/favicon.svg" width="72" alt="Tula's Canteen logo" />

# Tula's Canteen

**Order ahead. Skip the queue.**

A campus canteen pre-ordering and management system. Students order from their phone and pay from a
canteen wallet or at the counter. The kitchen gets orders on a live board, and food is handed over
by checking the student's order number and 4-digit pickup code (or scanning their QR).

![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![Tailwind CSS](https://img.shields.io/badge/Tailwind-4-06B6D4?logo=tailwindcss&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)

</div>

**🎬 Demo video:** [Watch the 2½-minute walkthrough](https://abhishek360007.github.io/Canteen-Management-System-/demo/) (captioned, end to end)
**🌐 Live demo:** [tulas-canteen.onrender.com](https://tulas-canteen.onrender.com) (free hosting: the first visit after a quiet period can take about a minute to wake up)
**📘 Project guide:** [Tulas-Canteen-Project-Guide.pdf](docs/Tulas-Canteen-Project-Guide.pdf), a 30-page walkthrough of every feature, the smart parts, the architecture and how to present the project

[![Demo: the live kitchen board and hand-over with a pickup code](docs/demo/demo-preview.gif)](https://abhishek360007.github.io/Canteen-Management-System-/demo/)

## 🎬 Demo Video

[**▶ Watch the full demo (2 min 28 s)**](https://abhishek360007.github.io/Canteen-Management-System-/demo/) (or [download the MP4](docs/demo/tulas-canteen-demo.mp4)). Every step is captioned:

1. Secure sign-in with role-based accounts
2. Today's menu: canteens, ratings, reviews and stock badges
3. Ordering ahead: pickup time, wallet, reward points and smart add-ons
4. The pickup pass: order number, 4-digit code, QR and live status
5. The kitchen's live order board
6. Hand-over with the student's pickup code
7. Rating dishes and reporting a problem
8. Admins replying to a complaint and refunding to the wallet
9. Dashboard, demand forecast and ingredients
10. Many canteens, one campus wallet

<p align="center">
  <img src="docs/screenshots/menu.png" width="230" alt="Student menu" />
  <img src="docs/screenshots/cart.png" width="230" alt="Cart with pickup time and payment" />
  <img src="docs/screenshots/pickup-pass.png" width="230" alt="Pickup pass with order number, code and QR" />
</p>
<p align="center">
  <img src="docs/screenshots/kitchen-board.png" width="700" alt="Kitchen live order board" />
</p>
<p align="center">
  <img src="docs/screenshots/admin-dashboard.png" width="700" alt="Admin dashboard" />
</p>

## Features

### Multiple canteens
- Any number of canteens on one campus (e.g. main canteen, food court, hostel mess), each with its **own menu,
  stock, kitchen staff, opening hours, rules and order numbers** (`T-017` at the main canteen, `F-017` at the food court)
- Students choose where to order, and that is their **pickup location**; one cart and one order per canteen
- Kitchen staff only see and serve their own canteen's orders and stock (enforced by the server)
- Admins switch canteens from the header, add new ones, and **compare** sales, order value, ratings and top dishes
- Wallet, rewards and UPI top-ups are campus-wide: one wallet works everywhere

### Students
- Sign up and log in (JWT); edit profile and change password
- Today's menu by category, with search, a veg-only filter, favourites, ratings and "only N left" stock badges
- Cart with a **pickup time slot** (or as soon as possible) and a note for the kitchen
- Pay from the **canteen wallet** or with **cash/UPI at the counter**
- **Pickup pass** with order number (e.g. `T-017`), a 4-digit pickup code and a QR code
- **Live status** (Placed → Cooking → Ready → Collected), queue position and estimated wait, with no page refresh
- Phone notification and vibration when the food is ready
- Cancel before cooking starts (instant wallet refund); rate the order after collecting
- **Dish reviews**: stars and a few words for each dish of a collected order; tap a dish's rating on the menu to
  read what other students said
- **Report a problem** with an order (wrong item, missing item, cold food, small portion, hygiene, late…) up to
  48 hours after pickup, pick the dishes it's about, and talk it through with the canteen in a chat-style thread;
  refunds go straight to the wallet
- Wallet page with balance, full transaction history and monthly spending
- **Pay online with Razorpay** (UPI, cards, netbanking, wallets): add money to the wallet instantly, or at checkout
  "Pay ₹X online & place order" pays just the amount the wallet is short
- **Add money with UPI**: pay the canteen's UPI ID from any UPI app (one tap on phones, QR on laptops), then
  send the UPI reference; it's added to the wallet once an admin confirms the payment
- **Smart recommendations**: "Recommended for you", "Order again", "Trending this week", add-ons in the cart
  ("60% of people who order Masala Dosa add Filter Coffee") and alternatives when a dish is sold out
- **Rewards**: 1 point per ₹10 on every collected order, Silver and Gold tiers (1.25× / 1.5×), use points at
  checkout (10 points = ₹5, up to half the order), a birthday bonus, and 50 points each for inviting a friend
- **Notifications**: a bell with every update, plus **phone alerts** (browser push) when food is ready, an order is
  cancelled and refunded, a payment or top-up goes through, or points are earned, even with the site closed

### Kitchen staff
- **Live order board** (New / Cooking / Ready) that updates instantly, with a chime for new orders
- One tap to start cooking or mark ready; cancel with a reason (the student is refunded automatically)
- **Hand-over check**: scan the student's QR or type the order number and code. Wrong codes are counted
  and the order locks after 5 attempts. Manual hand-over is available for special cases.
- **Stock control**: plates left per dish, on/off for today, unlimited dishes
- Canteen switch: **Open / Busy / Closed**
- **Day summary**: cash collected, cash still to collect, wallet sales, items sold
- **Plan for the coming days**: how many plates of each dish to prepare, and the ingredients needed
- Phone alerts when a dish is **running low or sold out**

### Admin
- **Dashboard**: today's sales, orders in progress, 7-day sales chart, busiest hours, top dishes, average rating, money held in wallets
- Menu management: add, edit and remove dishes (removed dishes keep their order history), plus a one-click
  **standard canteen menu** of 40+ everyday dishes (dosa, thali, biryani, snacks, chai, lassi, desserts) to start from
- User management: search, **recharge wallets**, see wallet history, block or unblock, create kitchen staff logins,
  **reset a forgotten password** (temporary password) and **log someone out on all devices**
- **UPI top-up approvals**: each request shows the amount and UPI reference to check before approving or rejecting
- **Payments**: switch online payment on/off, top-up limits, Razorpay test/live status, webhook setup and a list of
  every online payment for reconciliation
- **Activity log**: who recharged, approved, refunded, blocked, reset a password or changed the menu, and when
- All orders with date and status filters, plus **CSV export** for Excel
- Announcements shown at the top of every student's screen
- **Complaints**: open / resolved / closed lists per canteen, reply in the thread (with quick replies), then
  **resolve with a wallet refund** (one tap for a dish or the whole order, never more than the student paid) or
  close with a reason. 30-day summary: what goes wrong most, which dishes are named, refunds, average time to close
- **Review moderation**: reviews whose words look like a link, phone number, email or abuse wait for a check;
  admins can show or hide any review's words (its stars still count, so moderation can't bend a dish's rating)
- Canteens: opening hours, max orders in progress per student, max plates of one dish, no-show hold time, cash on/off
- **Forecast & insights**:
  - **Demand forecast** per dish for any of the next 7 days, with a likely range and a recommended number of plates
  - **Ingredient forecast** from per-plate recipes (e.g. 17 plates of Rajma Chawal → 1.19 kg rajma)
  - **Accuracy check**: what the forecast would have said on each of the last 14 days vs what actually sold
  - **Peak hours** heatmap by weekday and hour, and **best and least sellers** with month-on-month change
  - One click to use the forecast as tomorrow's daily plates

### Under the hood
- **Safe ordering under a lunch rush.** Placing an order is one database transaction: check the rules, reserve
  plates, take payment, save. Plate reservation and wallet debits are single conditional `UPDATE`s, so two
  students can never buy the last plate and a wallet can never go negative.
- **No double orders.** The app sends an `Idempotency-Key` with each checkout, so a double tap or a retry on
  bad Wi-Fi returns the same order instead of charging twice.
- **No double hand-over.** Orders use optimistic locking, so two staff phones can't both hand over the same order.
- **Wallet ledger.** Every recharge, payment and refund is recorded with the balance afterwards.
- **Live updates** with Server-Sent Events, sent only after the database change is committed.
- **Fair ratings and refunds**: each student's order counts once per dish (a dish review replaces the order's stars
  for that dish instead of adding a second vote), rating totals change with single atomic `UPDATE`s, and a complaint
  can be refunded only once (optimistic locking) and only up to what was actually paid (a cash no-show paid nothing).
- **Background jobs**: ready food not collected within the hold time becomes a no-show and its plates go back
  on sale; unfinished orders from earlier days are cancelled and refunded; stock resets every morning at 5 AM.
- Role-based access (Student / Staff / Admin) on every API path; blocked users are logged out immediately.
- **Login protection**: 5 wrong passwords lock that account for 15 minutes, failed logins and sign-ups are
  limited per network, and changing or resetting a password logs out every other device.
- **Holds up under a rush**: each order uses a single database connection, so a burst of simultaneous orders
  can't exhaust the small connection pool of a free hosted database (covered by a concurrency test).
- UPI top-ups can't be approved twice, and the same UPI reference can't be claimed twice.
- **How the "smart" parts work** (no black box, every result comes with a reason):
  - Recommendations use **basket analysis** over the last 60 days of orders: for every pair of dishes, *confidence*
    (how often B is bought when A is) and *lift* (whether that's more than chance), combined with each student's own
    recent orders, favourite categories, veg preference, what's trending and the time of day.
  - Forecasts blend the **same weekday over the last 4 weeks** (weighted 40/30/20/10%) with a **7-day weighted
    trend**, ignore days the canteen was closed, and add a 10% safety margin. They're checked against reality by
    replaying past days using only the data available at the time.
- **Browser push** follows the Web Push standards (RFC 8291 encryption, RFC 8292 VAPID keys stored in the database)
  and only talks to official push services (Google, Mozilla, Apple, Microsoft), so it can't be abused to reach other
  servers. Tested end to end: Chrome subscribed through Google's push service and Google accepted the message.
- **Upgrade-safe**: moving from the single-canteen version puts existing dishes, orders and staff into the main
  canteen automatically, and order numbering continues where it left off.
- **Online payments are safe by design**: the amount is fixed on the server, every payment is verified with
  Razorpay's HMAC-SHA256 signature (constant-time check), a signed webhook credits the wallet even if the student
  closes the app mid-payment, and a payment is credited exactly once however many confirmations arrive.
  Razorpay keys live only in server environment variables. Tested against a fake Razorpay API on H2 and PostgreSQL.
- **No lost updates**: saving an unrelated change (like blocking a user) never overwrites a wallet balance, points
  total or plates-left that changed at the same moment.
- Tests (60) run on both H2 and a real PostgreSQL server, including concurrency, forecasting, multi-canteen, complaints and reviews.
- Swagger API docs, an integration test for the whole order flow, light and dark mode, and a mobile-first layout.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA (Hibernate), Bean Validation, springdoc OpenAPI |
| Database | PostgreSQL (Neon, production), MySQL 8, H2 (zero-setup local runs and tests) |
| Frontend | React 19, Vite, Tailwind CSS 4, React Router, Lucide icons, `qrcode`, service worker for push |
| Notifications | Server-Sent Events (live screens), Web Push via `web-push` + Bouncy Castle |
| Deployment | Docker (one image with frontend and backend) and Docker Compose with MySQL |

## Run it locally

**Requirements:** JDK 21+, Maven 3.9+, Node.js 20+. MySQL is optional, since local runs use a built-in H2 database.

```bash
# 1. Backend: http://localhost:8080 (Swagger: /swagger-ui.html)
cd backend
mvn spring-boot:run

# 2. Frontend (new terminal): http://localhost:5173
cd frontend
npm install
npm run dev
```

`mvn spring-boot:run` starts in **demo mode**, which adds two canteens with menus, four weeks of sample sales
(so forecasts and insights have data) and these logins:

| Role | Email | Password |
|---|---|---|
| Student (₹500 in wallet) | `student@tulas.local` | `student123` |
| Kitchen (main canteen) | `kitchen@tulas.local` | `kitchen123` |
| Kitchen (Food Court) | `foodcourt@tulas.local` | `kitchen123` |
| Admin | `admin@tulas.local` | `Admin@123` |

To try it on a phone, connect to the same Wi-Fi and open `http://<your-PC-IP>:5173`.

**Run the demo with MySQL instead of H2:**

```bash
cd backend
DB_USERNAME=root DB_PASSWORD=yourpassword mvn spring-boot:run -Dspring-boot.run.profiles=demo,mysql
```

Run any other way (the jar, Docker) and demo mode is **off**: there are no demo logins and no
known passwords (see [Security defaults](#security-defaults)).

**Run the tests:**

```bash
cd backend
mvn test
```

## Deploy on Neon + Render (free)

**Live:** https://tulas-canteen.onrender.com

1. **Database:** create a project on [Neon](https://neon.tech) (region: Singapore) and copy the connection string
   (`postgresql://user:password@ep-xxx.aws.neon.tech/neondb?sslmode=require`).
2. **App:** on [Render](https://render.com) choose **New → Blueprint** and pick this repo. `render.yaml` sets up a
   Docker web service. Fill in `DATABASE_URL` (the Neon string, pasted as-is), `ADMIN_EMAIL` and `ADMIN_PASSWORD`.
3. Render builds the image, starts the app, and checks `/api/health`. Tables are created automatically on first start.

On Render's free plan the app sleeps after 15 minutes without visitors, and the first visit after that takes
about a minute to wake it.

## Deploy with Docker

```bash
cp .env.example .env      # then set real passwords and a long JWT_SECRET
docker compose up -d --build
```

The app runs on `http://localhost:8080`, with the React app served by the same server.

## Configuration

| Variable | Purpose | If not set |
|---|---|---|
| `JWT_SECRET` | Signs login tokens. Use 32+ random characters. | Random on every start (everyone is logged out on restart) |
| `ADMIN_EMAIL` | Admin account created on first start | `admin@tulas.local` |
| `ADMIN_PASSWORD` | Password for that admin account | Random, printed once in the server log |
| `SPRING_PROFILES_ACTIVE` | `mysql` to use MySQL | Built-in H2 database |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection | `localhost`, `3306`, `tulas_canteen` |
| `SEED_DEMO` | Add the demo menu and logins | `false` (`true` in demo mode) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | Online payment (`rzp_test_…` = test mode, `rzp_live_…` = real money) | Online payment off |
| `RAZORPAY_WEBHOOK_SECRET` | Verifies Razorpay's webhook (credits the wallet even if the app is closed mid-payment) | Webhook refused |
| `CORS_ORIGINS` | Allowed frontend origins (comma-separated) | `http://localhost:5173` |

### Security defaults

The app is safe by default, with no well-known passwords outside demo mode:

- If `ADMIN_PASSWORD` isn't set, a random admin password is generated on the first start and printed once
  in the server log. Log in with it and change it under **Account**.
- If `JWT_SECRET` isn't set, a random secret is used. Logins still work, but everyone is logged out when the
  server restarts, so set a fixed `JWT_SECRET` for real use.
- Demo data and demo logins appear only in demo mode (`mvn spring-boot:run`).

When you go live, also serve the site over HTTPS.

## Project structure

```
backend/                     Spring Boot API
  src/main/java/com/tulas/canteen/
    domain/                  JPA entities: User, MenuItem, Order, OrderItem, WalletTxn, ...
    repo/                    Spring Data repositories (atomic stock and wallet updates)
    service/                 Business logic: OrderService, WalletService, MenuService, ReportService, LiveHub, jobs
    web/                     REST controllers: auth, student, kitchen, admin
    security/                JWT service and filter
    config/                  Security, errors, demo data, SPA hosting
  src/test/                  End-to-end API test of the order flow
frontend/                    React app
  src/pages/student|kitchen|admin
  src/components             UI kit, charts, QR code and scanner
  src/context                Auth, cart, live updates, theme
docs/screenshots/
Dockerfile, docker-compose.yml
```

## Main API endpoints

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET/PUT /api/auth/me`, `POST /api/auth/password` |
| Everyone | `GET /api/menu`, `GET /api/canteen`, `GET /api/announcements`, `GET /api/live` (SSE) |
| Student | `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{id}`, `POST /api/orders/{id}/cancel`, `POST /api/orders/{id}/rate`, `GET /api/wallet`, `PUT/DELETE /api/favourites/{id}` |
| Kitchen | `GET /api/kitchen/queue`, `POST /api/kitchen/orders/{id}/status`, `POST /api/kitchen/verify`, `PUT /api/kitchen/menu/{id}/stock`, `PUT /api/kitchen/status`, `GET /api/kitchen/summary` |
| Admin | `GET /api/admin/dashboard`, `/api/admin/users`, `/api/admin/users/{id}/recharge`, `/api/admin/menu`, `/api/admin/orders`, `/api/admin/orders.csv`, `/api/admin/announcements`, `/api/admin/settings` |

The full interactive docs are at `/swagger-ui.html`.

## Author

**Abhishek Raj**: [GitHub](https://github.com/Abhishek360007)

## License

[MIT](LICENSE) © 2026 Abhishek Raj
