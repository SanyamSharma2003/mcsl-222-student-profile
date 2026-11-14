#!/usr/bin/env python3
"""Black-box test of the fictional demo only. Run inside the Codespace.

Uses Python stdlib. Restores address/email through the application's own forms;
does not delete records, reset the database, or delete Mailpit messages.
Mailpit API: https://mailpit.axllent.org/docs/api-v1/ and its linked Swagger schema.
"""
import argparse
import html
from html.parser import HTMLParser
import http.cookiejar
import json
import re
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


class Controls(HTMLParser):
    def __init__(self, source):
        super().__init__(convert_charrefs=True)
        self.fields, self.parts, self.textarea = {}, [], None
        self.feed(source)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "input" and attrs.get("name"):
            self.fields[attrs["name"]] = attrs.get("value", "")
        if tag == "textarea":
            self.textarea = attrs.get("name")
            self.fields[self.textarea] = ""

    def handle_endtag(self, tag):
        if tag == "textarea":
            self.textarea = None

    def handle_data(self, data):
        self.parts.append(data)
        if self.textarea:
            self.fields[self.textarea] += data

    @property
    def text(self):
        return " ".join(" ".join(self.parts).split())


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="https://localhost:8443/student-profile")
    parser.add_argument("--skip-email", action="store_true")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    origin = urllib.parse.urlsplit(base)
    require(origin.scheme == "https" and origin.hostname in {"localhost", "127.0.0.1"}
            and not origin.query and not origin.fragment, "Only local HTTPS demo targets are allowed")
    cookies = http.cookiejar.CookieJar()
    # This exception is confined to the generated self-signed LOCAL demo endpoint.
    tls = ssl._create_unverified_context()
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),
        urllib.request.HTTPCookieProcessor(cookies), urllib.request.HTTPSHandler(context=tls))

    def request(path, fields=None):
        data = None if fields is None else urllib.parse.urlencode(fields).encode()
        req = urllib.request.Request(base + path, data=data)
        try:
            with opener.open(req, timeout=15) as response:
                return response.status, response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode("utf-8")

    def get(path="/profile"):
        status, source = request(path)
        require(status == 200, f"GET {path.split('?')[0]} returned HTTP {status}")
        return Controls(source)

    def post(fields):
        page = get()
        status, source = request("/profile", {"csrf": page.fields["csrf"], **fields})
        require(status == 200, f"Profile POST returned HTTP {status}")
        return Controls(source)

    def lookup(token):
        return post({"action": "lookup", "lookup": token})

    def save(page, address=None, email=None):
        return post({"action": "save", "version": page.fields["version"],
            "address": page.fields["address"] if address is None else address,
            "mobile": page.fields["mobile"],
            "email": page.fields["email"] if email is None else email})

    mail_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def mail_json(path):
        with mail_opener.open("http://localhost:8025" + path, timeout=10) as response:
            return json.load(response)

    def ids_for(recipient):
        query = urllib.parse.urlencode({"query": "to:" + recipient, "limit": 100})
        return mail_json("/api/v1/search?" + query).get("messages", [])

    def confirm_email(recipient, previous_ids):
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            for summary in ids_for(recipient):
                if summary["ID"] in previous_ids or summary.get("Subject") != "Confirm your university email address":
                    continue
                message = mail_json("/api/v1/message/" + urllib.parse.quote(summary["ID"], safe=""))
                match = re.search(r"https://[^\s]+[?]verify=([A-Za-z0-9_-]{43})", message.get("Text", ""))
                if not match:
                    continue
                # Read the delivered token, but keep the request on the local test origin.
                page = get("/profile?verify=" + match.group(1))
                require("Confirm your new email address" in page.text, "Email confirmation form missing")
                page = post({"action": "verifyEmail", "token": page.fields["token"]})
                require("Email verified successfully" in page.text, "Email confirmation failed")
                return lookup("6000000001")
            time.sleep(2)
        raise AssertionError("Verification email was not delivered within 100 seconds")

    page = get("/login")
    status, source = request("/login", {"csrf": page.fields["csrf"],
        "username": "demo1", "password": "LabDemo!2026"})
    require(status == 200 and "Student Profile Update" in source, "Demo login failed")
    print("PASS demo login and JSP rendering", flush=True)

    page = lookup("6000000001")
    require("Demo Student One" in page.text and "version" in page.fields, "Enrollment lookup failed")
    original = dict(page.fields)
    current_match = re.search(r"Current email: (\S+) \(verified\)", page.text)
    require(current_match is not None, "Expected fictional demo account with verified current email")
    original_email = current_match.group(1)
    had_pending = "Pending email:" in page.text
    print("PASS enrollment lookup", flush=True)

    page = lookup(original["mobile"])
    require("Demo Student One" in page.text and "version" in page.fields, "Mobile lookup failed")
    print("PASS mobile lookup", flush=True)
    page = lookup("240000000002")
    require("No matching profile" in page.text and "version" not in page.fields,
            "Cross-owner lookup exposed another learner")
    print("PASS cross-owner lookup denied", flush=True)

    status, _ = request("/profile", {"action": "lookup", "lookup": "6000000001", "csrf": "invalid"})
    require(status == 403, "Invalid CSRF token was not rejected")
    print("PASS invalid CSRF rejected", flush=True)

    address_changed = False
    try:
        page = lookup("6000000001")
        test_address = "Fictional smoke test address " + uuid.uuid4().hex[:10]
        page = save(page, address=test_address)
        address_changed = True
        require("Profile saved." in page.text and page.fields.get("address") == test_address,
                "Address save was not persisted")
        print("PASS address save persisted", flush=True)
    finally:
        if address_changed:
            page = save(lookup("6000000001"), address=original["address"])
            require(page.fields.get("address") == original["address"], "Address restoration failed")
            print("PASS original address restored", flush=True)

    if args.skip_email or had_pending:
        print("SKIP email test (requested or an earlier pending change exists)", flush=True)
        return
    mail_json("/api/v1/info")  # Fail before changing email if the test mailbox is unavailable.
    test_email = "smoke-" + uuid.uuid4().hex[:12] + "@example.test"
    before = {item["ID"] for item in ids_for(test_email)}
    email_changed = False
    try:
        page = save(lookup("6000000001"), email=test_email)
        email_changed = True
        require("Pending email: " + test_email in page.text and
                "Current email: " + original_email in page.text, "New email did not remain pending")
        print("PASS new email remains pending before verification", flush=True)
        page = confirm_email(test_email, before)
        require("Current email: " + test_email + " (verified)" in page.text,
                "Verified email was not made current")
        print("PASS outbox delivery and email ownership confirmation", flush=True)
    finally:
        if email_changed:
            before = {item["ID"] for item in ids_for(original_email)}
            page = save(lookup("6000000001"), email=original_email)
            if "Pending email: " + original_email in page.text:
                page = confirm_email(original_email, before)
            require("Current email: " + original_email + " (verified)" in page.text and
                    "Pending email:" not in page.text, "Original email restoration failed")
            print("PASS original verified email restored", flush=True)
    print("ALL LIVE SMOKE CHECKS PASSED", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"FAIL: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(1)
