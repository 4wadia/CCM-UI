package com.forma.cards;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locally imports transaction-shaped notifications from common SMS and email apps.
 * It never uploads notification text; only parsed transaction fields are persisted.
 */
public final class AlertNotificationListener extends NotificationListenerService {
    private static final String PREFS = "forma";
    private static final String IMPORTS = "imports";
    private static final String SEEN = "seen_alerts";
    private static final int MAX_IMPORTS = 200;
    private static final int MAX_SEEN = 300;
    private static final long SEEN_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    private static final Set<String> SMS_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging",
            "com.android.messaging",
            "com.samsung.android.messaging",
            "com.oneplus.mms",
            "com.miui.mms"
    ));
    private static final Set<String> MAIL_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail"
    ));

    private static final Pattern AMOUNT = Pattern.compile("(?i)(?:₹\\s*|INR\\s*|Rs\\.?\\s*|USD\\s*|\\$\\s*)([0-9,]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern LAST_FOUR = Pattern.compile("(?i)(?:ending|last\\s*(?:4|four)?\\s*digits?|card\\s*(?:ending|no\\.?|number)\\s*[:#\\-]?)\\s*(\\d{4})|(?:\\*{2,}|x{2,})\\s*(\\d{4})\\b");
    private static final Pattern MERCHANT = Pattern.compile("(?i)\\b(?:at|to|from)\\s+['\"]?([^\\n,;.]{2,48}?)(?=\\s+(?:on|using|with|via|from|for|card|ending|\\*{2,}|x{2,})\\b|[,;\\n.]|$)");
    private static final Pattern TRANSACTION_WORDS = Pattern.compile("(?i)\\b(spent|debited|debit|purchase|transaction|txn|charged|payment|used|withdrawn|transferred)\\b");
    private static final Pattern NON_TRANSACTION = Pattern.compile("(?i)\\b(otp|one[ -]?time password|verification code|available limit|credit limit|statement generated|minimum due|payment due|offer|offer expires)\\b");

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        String packageName = sbn.getPackageName();
        final String source;
        if (SMS_PACKAGES.contains(packageName)) source = "SMS alert";
        else if (MAIL_PACKAGES.contains(packageName)) source = "Email alert";
        else return;

        String body = notificationText(sbn.getNotification());
        if (body.isEmpty() || NON_TRANSACTION.matcher(body).find() || !TRANSACTION_WORDS.matcher(body).find()) return;
        Matcher amountMatcher = AMOUNT.matcher(body);
        if (!amountMatcher.find()) return;

        long amount;
        try {
            amount = Math.round(Double.parseDouble(amountMatcher.group(1).replace(",", "")));
        } catch (Exception ignored) {
            return;
        }
        if (amount <= 0) return;

        String title = notificationTitle(sbn.getNotification());
        String merchant = extractMerchant(body, title);
        String lastFour = extractLastFour(body);
        int cardIndex = findCardIndex(lastFour);
        long postedAt = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();
        String fingerprint = fingerprint((merchant + "|" + amount + "|" + lastFour + "|" + body).toLowerCase(Locale.ROOT));
        persistIfNew(fingerprint, merchant, amount, source, cardIndex, postedAt);
    }

    private String notificationText(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return "";
        StringBuilder text = new StringBuilder();
        append(text, extras.getCharSequence(Notification.EXTRA_TITLE));
        append(text, extras.getCharSequence(Notification.EXTRA_TEXT));
        append(text, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) append(text, line);
        return text.toString().trim();
    }

    private String notificationTitle(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return "";
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        return title == null ? "" : title.toString().trim();
    }

    private void append(StringBuilder into, CharSequence value) {
        if (value == null) return;
        String cleaned = value.toString().trim();
        if (!cleaned.isEmpty()) into.append(cleaned).append('\n');
    }

    private String extractMerchant(String body, String title) {
        Matcher matcher = MERCHANT.matcher(body);
        if (matcher.find()) {
            String found = matcher.group(1).trim().replaceAll("(?i)\\s+(?:INR|Rs\\.?|₹).*?$", "").trim();
            if (found.length() > 1) return found;
        }
        if (!title.isEmpty() && !title.matches("(?i).*(bank|transaction|debit|credit|alert|message|gmail|outlook).*")) return title;
        return "Card transaction";
    }

    private String extractLastFour(String body) {
        Matcher matcher = LAST_FOUR.matcher(body);
        if (!matcher.find()) return "";
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private int findCardIndex(String lastFour) {
        if (lastFour == null || lastFour.isEmpty()) return -1;
        try {
            JSONArray cards = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString("cards", "[]"));
            for (int i = 0; i < cards.length(); i++) {
                JSONObject card = cards.optJSONObject(i);
                if (card != null && lastFour.equals(card.optString("last", ""))) return i;
            }
        } catch (Exception ignored) { }
        return -1;
    }

    private void persistIfNew(String fingerprint, String merchant, long amount, String source, int cardIndex, long time) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray imports;
        JSONArray seen;
        try { imports = new JSONArray(prefs.getString(IMPORTS, "[]")); } catch (Exception e) { imports = new JSONArray(); }
        try { seen = new JSONArray(prefs.getString(SEEN, "[]")); } catch (Exception e) { seen = new JSONArray(); }

        long cutoff = System.currentTimeMillis() - SEEN_TTL_MS;
        List<String> keep = new ArrayList<>();
        for (int i = 0; i < seen.length(); i++) {
            JSONObject item = seen.optJSONObject(i);
            if (item == null || item.optLong("time", 0) < cutoff) continue;
            String hash = item.optString("hash", "");
            if (!hash.isEmpty()) keep.add(hash);
        }
        if (keep.contains(fingerprint)) return;

        try {
            JSONObject tx = new JSONObject();
            tx.put("icon", source.startsWith("Email") ? "✉" : "↗");
            tx.put("merchant", merchant);
            tx.put("date", "Just now");
            tx.put("amount", money(amount));
            tx.put("source", source);
            tx.put("card", cardIndex);
            tx.put("time", time);
            JSONArray updatedImports = new JSONArray();
            updatedImports.put(tx);
            for (int i = 0; i < imports.length() && updatedImports.length() < MAX_IMPORTS; i++) {
                JSONObject existing = imports.optJSONObject(i);
                if (existing != null) updatedImports.put(existing);
            }

            JSONArray updatedSeen = new JSONArray();
            updatedSeen.put(new JSONObject().put("hash", fingerprint).put("time", System.currentTimeMillis()));
            int from = Math.max(0, keep.size() - (MAX_SEEN - 1));
            for (int i = from; i < keep.size(); i++) updatedSeen.put(new JSONObject().put("hash", keep.get(i)).put("time", System.currentTimeMillis()));
            prefs.edit().putString(IMPORTS, updatedImports.toString()).putString(SEEN, updatedSeen.toString()).apply();
        } catch (Exception ignored) { }
    }

    private String money(long value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);
        return format.format(value);
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
