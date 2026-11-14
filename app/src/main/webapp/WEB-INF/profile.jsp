<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head><meta charset="UTF-8"><title>Student Profile Update</title></head>
<body>
<h1>Student Profile Update</h1>
<c:url var="logoutUrl" value="/logout"/>
<p>Local lab demo with sample student data. <a href="${logoutUrl}">Sign out</a></p>
<p>Use your enrollment number or registered mobile number to find your own profile.</p>
<c:if test="${not empty message}">
  <p role="status"><c:out value="${message}"/></p>
</c:if>
<c:url var="profileUrl" value="/profile"/>

<c:choose>
  <c:when test="${not empty verificationToken}">
    <h2>Confirm your new email address</h2>
    <form method="post" action="${profileUrl}">
      <input type="hidden" name="action" value="verifyEmail">
      <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
      <input type="hidden" name="token" value="<c:out value='${verificationToken}'/>">
      <button type="submit">Confirm email change</button>
    </form>
  </c:when>
  <c:otherwise>
    <form method="post" action="${profileUrl}">
      <input type="hidden" name="action" value="lookup">
      <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
      <label for="lookup">Enrollment number or mobile number</label>
      <input id="lookup" name="lookup" type="text" maxlength="20" required autocomplete="off">
      <button type="submit">Find my profile</button>
    </form>

    <c:if test="${not empty profile}">
      <h2>Verified student record</h2>
      <p>Name: <c:out value="${profile.name}"/></p>
      <p>Enrollment: <c:out value="${profile.enrollment}"/></p>
      <p>Programme: <c:out value="${profile.programme}"/></p>
      <p>Current email: <c:out value="${profile.email}"/>
        (<c:choose><c:when test="${profile.emailVerified}">verified</c:when><c:otherwise>unverified</c:otherwise></c:choose>)
      </p>
      <c:if test="${not empty profile.pendingEmail}">
        <p>Pending email: <c:out value="${profile.pendingEmail}"/> — confirmation required.</p>
      </c:if>
      <c:if test="${not profile.mobileVerified}">
        <p>Your contact mobile is unverified. It is not an authentication or recovery number.</p>
      </c:if>

      <form method="post" action="${profileUrl}">
        <input type="hidden" name="action" value="save">
        <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
        <input type="hidden" name="version" value="<c:out value='${profile.version}'/>">
        <p>
          <label for="address">Communication address</label><br>
          <textarea id="address" name="address" rows="4" cols="55" minlength="5" maxlength="500" required><c:out value="${profile.address}"/></textarea>
        </p>
        <p>
          <label for="mobile">Contact mobile</label><br>
          <input id="mobile" name="mobile" type="tel" maxlength="13" required
                 value="<c:out value='${profile.mobile}'/>">
        </p>
        <p>
          <label for="email">Requested email address</label><br>
          <input id="email" name="email" type="email" maxlength="254" required
                 value="<c:out value='${profile.requestedEmail}'/>">
        </p>
        <p>A new email address becomes current only after confirmation. To resend an expired link, save again.</p>
        <button type="submit">Save contact details</button>
      </form>
    </c:if>
  </c:otherwise>
</c:choose>
</body>
</html>
