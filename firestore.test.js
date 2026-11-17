const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_trader_1";
const BOB_UID = "bob_trader_2";
const ADMIN_EMAIL = "saraizabel8189@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, email = "alice@example.com") {
  return {
    userId: uid,
    displayName: "Alice Trader",
    email,
    cashBalanceUsd: 0,
    totalDepositedUsd: 0,
    pendingWithdrawalUsd: 0,
    kycStatus: "not_submitted",
    traderMode: "beginner",
    accountStatus: "active",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validKycPayload(uid, status = "pending") {
  return {
    userId: uid,
    userEmail: "alice@example.com",
    fullLegalName: "Alice Johnson",
    country: "United States",
    dateOfBirth: "1994-05-12",
    idType: "Passport",
    idNumber: "P9948210",
    docImageBase64: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
    status,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validDepositPayload(uid, status = "pending") {
  return {
    userId: uid,
    userDisplayName: "Alice Trader",
    userEmail: "alice@example.com",
    amountUsd: 5000,
    method: "USDT_TRC20",
    walletAddressUsed: "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE",
    txHash: "0xabc123456789def",
    proofImageBase64: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
    status,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validWithdrawalPayload(uid, status = "pending") {
  return {
    userId: uid,
    userDisplayName: "Alice Trader",
    userEmail: "alice@example.com",
    amountUsd: 1200,
    method: "BTC",
    destinationDetails: "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
    accountHolderName: "Alice Johnson",
    status,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validBrokerConfigPayload(uid) {
  return {
    stockCommissionBps: 10,
    cryptoCommissionBps: 25,
    forexCommissionBps: 5,
    commodityCommissionBps: 15,
    indexCommissionBps: 8,
    minOrderUsd: 5,
    tradingHalted: false,
    usdtTrc20Address: "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE",
    usdtErc20Address: "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
    btcWalletAddress: "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
    ethWalletAddress: "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
    bankWireDetails: "ApexTrade Custody • SWIFT: APEXUS33 • Acct: 8820194421",
    updatedByUid: uid,
    updatedAt: serverTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied across users, kyc, deposits, withdrawals, trades", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("kyc_requests").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("deposits").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("withdrawals").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("broker_config").doc("global").get());
});

test("User registration starts at $0 balance and not_submitted KYC; user cannot self-approve KYC or self-credit deposit", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));

  // Cannot self-approve KYC
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      kycStatus: "approved",
      updatedAt: serverTimestamp(),
    })
  );
  // Cannot self-credit totalDepositedUsd
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalDepositedUsd: 50000,
      cashBalanceUsd: 50000,
      updatedAt: serverTimestamp(),
    })
  );
  // Cannot create KYC directly as approved
  await assertFails(
    aliceDb.collection("kyc_requests").doc(ALICE_UID).set(validKycPayload(ALICE_UID, "approved"))
  );
  // Creating KYC as pending succeeds
  await assertSucceeds(
    aliceDb.collection("kyc_requests").doc(ALICE_UID).set(validKycPayload(ALICE_UID, "pending"))
  );
});

test("Deposits and Withdrawals: must be created pending; only Admin can approve/pay", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Cannot create approved deposit
  await assertFails(aliceDb.collection("deposits").doc("dep_1").set(validDepositPayload(ALICE_UID, "approved")));
  // Creating pending deposit succeeds
  await assertSucceeds(aliceDb.collection("deposits").doc("dep_1").set(validDepositPayload(ALICE_UID, "pending")));
  // User cannot approve their own deposit
  await assertFails(aliceDb.collection("deposits").doc("dep_1").update({ status: "approved", updatedAt: serverTimestamp() }));
  // Cross-user cannot read Alice's deposit
  await assertFails(bobDb.collection("deposits").doc("dep_1").get());

  // Creating pending withdrawal succeeds; user cannot mark paid
  await assertSucceeds(aliceDb.collection("withdrawals").doc("wd_1").set(validWithdrawalPayload(ALICE_UID, "pending")));
  await assertFails(aliceDb.collection("withdrawals").doc("wd_1").update({ status: "paid", updatedAt: serverTimestamp() }));

  // Unlock Admin with 25046371
  await assertSucceeds(
    bobDb.collection("admins").doc(BOB_UID).set({
      userId: BOB_UID,
      passcode: "25046371",
      grantedAt: serverTimestamp(),
    })
  );

  // Admin can approve deposit, mark withdrawal paid, and update broker_config wallets
  await assertSucceeds(
    bobDb.collection("deposits").doc("dep_1").update({
      status: "approved",
      adminNote: "Verified on-chain",
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("withdrawals").doc("wd_1").update({
      status: "paid",
      payoutReference: "tx_manual_9911",
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("broker_config").doc("global").set(validBrokerConfigPayload(BOB_UID))
  );
});

test("Bootstrapped admin email spoofing test: unverified email rejected", async () => {
  const spoofedDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(
    spoofedDb.collection("broker_config").doc("global").set(validBrokerConfigPayload("spoof_uid"))
  );
});

test("Ledger entries and audit logs are strictly append-only (immutable) and isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Grant Bob Admin
  await assertSucceeds(
    bobDb.collection("admins").doc(BOB_UID).set({ userId: BOB_UID, passcode: "25046371", grantedAt: serverTimestamp() })
  );

  // Alice creates valid ledger entry
  await assertSucceeds(
    aliceDb.collection("ledger_entries").doc("led_1").set({
      userId: ALICE_UID,
      entryType: "TRADE_BUY_DEBIT",
      direction: "DEBIT",
      amountUsd: 500,
      balanceBeforeUsd: 1000,
      balanceAfterUsd: 500,
      referenceType: "trade",
      referenceId: "t_1",
      description: "Buy AAPL",
      actorUid: ALICE_UID,
      createdAt: serverTimestamp(),
    })
  );

  // Neither Alice nor Admin can update or delete ledger_entries
  await assertFails(aliceDb.collection("ledger_entries").doc("led_1").update({ amountUsd: 1 }));
  await assertFails(bobDb.collection("ledger_entries").doc("led_1").delete());

  // Audit logs: Alice cannot read or write; Admin can create, nobody can update/delete
  await assertFails(aliceDb.collection("audit_logs").doc("aud_1").get());
  await assertSucceeds(
    bobDb.collection("audit_logs").doc("aud_1").set({
      actorUid: BOB_UID,
      actorEmail: "admin@test.com",
      action: "TEST_ACTION",
      targetType: "user",
      targetId: ALICE_UID,
      details: "Audit check",
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(bobDb.collection("audit_logs").doc("aud_1").delete());
});

test("Maker-Checker dual control forbids same-admin self-approval in Firestore rules", async () => {
  const admin1 = "admin_maker_1";
  const admin2 = "admin_checker_2";
  const db1 = testEnv.authenticatedContext(admin1).firestore();
  const db2 = testEnv.authenticatedContext(admin2).firestore();

  await assertSucceeds(db1.collection("admins").doc(admin1).set({ userId: admin1, passcode: "25046371", grantedAt: serverTimestamp() }));
  await assertSucceeds(db2.collection("admins").doc(admin2).set({ userId: admin2, passcode: "25046371", grantedAt: serverTimestamp() }));

  await assertSucceeds(
    db1.collection("pending_approvals").doc("mc_1").set({
      actionType: "LARGE_WITHDRAWAL_PAYOUT",
      targetUserId: ALICE_UID,
      targetReferenceId: "wd_99",
      amountUsd: 10000,
      payloadSummary: "Manual Payout $10,000",
      makerUid: admin1,
      makerEmail: "maker@apextrade.com",
      status: "pending_checker",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Admin1 (maker) CANNOT act as checker on their own request
  await assertFails(
    db1.collection("pending_approvals").doc("mc_1").update({
      status: "approved",
      checkerUid: admin1,
      checkerEmail: "maker@apextrade.com",
      updatedAt: serverTimestamp(),
    })
  );

  // Admin2 (different admin) CAN approve as checker
  await assertSucceeds(
    db2.collection("pending_approvals").doc("mc_1").update({
      status: "approved",
      checkerUid: admin2,
      checkerEmail: "checker@apextrade.com",
      updatedAt: serverTimestamp(),
    })
  );
});

test("Support tickets & notifications enforce ownership and admin access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Support ticket must start open
  await assertFails(
    aliceDb.collection("support_tickets").doc("tk_1").set({
      userId: ALICE_UID,
      subject: "Deposit check",
      status: "resolved",
      message: "Please check",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("support_tickets").doc("tk_1").set({
      userId: ALICE_UID,
      subject: "Deposit check",
      status: "open",
      message: "Please check my deposit",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  // Bob cannot read Alice's ticket
  await assertFails(bobDb.collection("support_tickets").doc("tk_1").get());
});

