# Frontend Integration Guide - Email-Based Session Management

This guide outlines the frontend changes required to integrate with the new email-based session management system.

## Overview

The application now uses email-based session management instead of temporary session IDs. Users provide their email address to initialize or resume sessions, and all repository analysis data persists across browser refreshes and application restarts.

## Required Frontend Changes

### 1. Email Collection Interface

Create a user email input form as the entry point:

```javascript
// Example: Email collection component
const EmailForm = () => {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    
    try {
      const response = await fetch('/api/user/session/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email })
      });
      
      const data = await response.json();
      if (data.success) {
        // Store email and proceed to main app
        localStorage.setItem('userEmail', email);
        // Handle session initialization...
      }
    } catch (error) {
      console.error('Session initialization failed:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input 
        type="email" 
        value={email} 
        onChange={(e) => setEmail(e.target.value)}
        placeholder="Enter your email"
        required 
      />
      <button type="submit" disabled={loading}>
        {loading ? 'Starting...' : 'Start Session'}
      </button>
    </form>
  );
};
```

### 2. Browser Storage Management

Store the user email in localStorage for session persistence:

```javascript
// Store email after successful session start
localStorage.setItem('userEmail', userEmail);

// Retrieve email on app load
const userEmail = localStorage.getItem('userEmail');

// Clear email on logout/session end
localStorage.removeItem('userEmail');
```

### 3. API Call Updates

**BEFORE (Session ID approach):**
```javascript
// Old approach - using sessionId
fetch('/api/chat/message', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    sessionId: currentSessionId,
    message: userMessage
  })
});
```

**AFTER (Email approach):**
```javascript
// New approach - using userEmail
const userEmail = localStorage.getItem('userEmail');

fetch('/api/chat/message', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    userEmail: userEmail,
    message: userMessage,
    repositoryUrl: currentRepositoryUrl // if continuing with specific repo
  })
});
```

### 4. New API Endpoints Integration

#### Session Initialization
```javascript
const initializeSession = async (email) => {
  const response = await fetch('/api/user/session/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  
  const data = await response.json();
  return {
    user: data.user,
    session: data.session,
    repositories: data.repositories,
    isNewUser: data.user.isNew
  };
};
```

#### Repository Selection
```javascript
const getUserRepositories = async (email) => {
  const response = await fetch(`/api/user/${email}/repositories`);
  const data = await response.json();
  return data.repositories;
};

const continueWithRepository = async (email, repositoryUrl) => {
  const response = await fetch('/api/user/session/continue', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, repositoryUrl })
  });
  
  return await response.json();
};
```

#### Current Session Check
```javascript
const getCurrentSession = async (email) => {
  const response = await fetch(`/api/user/${email}/session`);
  const data = await response.json();
  
  if (data.hasSession) {
    return data.session;
  }
  return null;
};
```

### 5. Application Flow Updates

#### New User Flow:
1. Show email collection form
2. Call `/api/user/session/start`
3. If `isNewUser: true`, show repository analysis options
4. Store email in localStorage
5. Proceed to main application

#### Returning User Flow:
1. Check localStorage for stored email
2. If found, call `/api/user/session/start` to restore session
3. Display user's previous repositories with option to continue
4. If user selects a repository, call `/api/user/session/continue`
5. Proceed to main application with context restored

### 6. Repository Management Interface

Create a repository selection component for returning users:

```javascript
const RepositorySelector = ({ repositories, onSelectRepository }) => {
  return (
    <div className="repository-list">
      <h3>Your Previous Repositories</h3>
      {repositories.map(repo => (
        <div key={repo.url} className="repository-item">
          <h4>{repo.name}</h4>
          <p>Owner: {repo.owner}</p>
          <p>Status: {repo.status}</p>
          <p>Files: {repo.fileCount}</p>
          <p>Analyzed: {new Date(repo.analyzedAt).toLocaleDateString()}</p>
          <button onClick={() => onSelectRepository(repo.url)}>
            Continue Analysis
          </button>
        </div>
      ))}
    </div>
  );
};
```

### 7. Error Handling

Add proper error handling for the new session system:

```javascript
const handleApiError = (error, context) => {
  console.error(`Error in ${context}:`, error);
  
  // Clear invalid session data
  if (error.status === 401 || error.status === 403) {
    localStorage.removeItem('userEmail');
    // Redirect to email collection
  }
  
  // Show user-friendly error messages
  showNotification(`Failed to ${context}. Please try again.`);
};
```

## Migration Checklist

- [ ] Create email collection interface
- [ ] Update all API calls to use `userEmail` instead of `sessionId`
- [ ] Implement localStorage management for email persistence
- [ ] Add repository selection interface for returning users
- [ ] Update error handling for new session system
- [ ] Test complete user flows (new user and returning user)
- [ ] Update any session-related UI components
- [ ] Remove old session ID logic and references

## Breaking Changes

1. **Session ID Removal**: The `sessionId` parameter is no longer used in API calls
2. **New Required Parameter**: All chat and analysis APIs now require `userEmail`
3. **Session Persistence**: Sessions now persist across browser refreshes and app restarts
4. **Repository Context**: Repository selection is now explicit via the continue session endpoint

## Benefits of New System

- **Persistent Sessions**: User data survives browser refreshes and app restarts
- **Multi-Repository Support**: Users can easily switch between their analyzed repositories
- **Simple Authentication**: No complex login system, just email-based identification
- **Better UX**: Returning users can immediately resume where they left off