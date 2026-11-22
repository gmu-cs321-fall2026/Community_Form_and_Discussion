console.log("testPost.js loaded");
// API Base URL
const API_BASE_URL = "http://localhost:8080/api";

// Helper function to grab the active user dynamically from the locked author input
function getCurrentUser() {
    const authorInput = document.getElementById('postAuthor');
    return authorInput && authorInput.value.trim() ? authorInput.value.trim() : "AlexUser";
}

const currentUserRole = "CAREER_STAFF";

let activeCategory = '';
let activeSearch = '';
let activeSort = 'recent';

function viewUserProfile(targetUserId) {
    window.location.href = `profile.html?user=${encodeURIComponent(targetUserId)}`;
}

function isCurrentUserPostCreator(post) {
    const currentUser = getCurrentUser().trim().toLowerCase();
    if (!currentUser || !post) return false;

    const authorIdMatch = post.authorId && String(post.authorId).trim().toLowerCase() === currentUser;
    const authorNameMatch = post.authorName && String(post.authorName).trim().toLowerCase() === currentUser;
    return Boolean(authorIdMatch || authorNameMatch);
}

// 1. Render Feed from Spring Boot Database
async function renderFeed(categoryId = activeCategory, searchQuery = activeSearch, sortBy = activeSort) {
    activeCategory = categoryId;
    activeSearch = searchQuery;
    activeSort = sortBy;

    const feed = document.getElementById('postsFeed');
    if (!feed) return;

    try {
        const params = new URLSearchParams();
        if (categoryId) params.append('categoryId', categoryId);
        if (searchQuery) params.append('search', searchQuery);
        if (sortBy) params.append('sortBy', sortBy);

        const url = `${API_BASE_URL}/posts${params.toString() ? '?' + params.toString() : ''}`;
        const response = await fetch(url);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);

        const raw = await response.json();
        let posts = Array.isArray(raw) ? raw : (raw.posts || []);

        posts = posts.filter(p => !p.status || p.status.toLowerCase() !== 'removed');

        if (sortBy === 'unanswered') {
            posts = posts.filter(p => !p.replies || p.replies.length === 0);
        } else if (sortBy === 'trending') {
            const sevenDaysAgo = Date.now() - (7 * 24 * 60 * 60 * 1000);
            const recentPosts = posts.filter(p => new Date(p.createdAt).getTime() >= sevenDaysAgo);
            if (recentPosts.length > 0) {
                posts = recentPosts;
            }
        }

        feed.innerHTML = '';

        const getEngagementScore = (p) => {
            const replyCount = p.replies ? p.replies.length : 0;
            const upvoteCount = Array.isArray(p.upvotes)
                ? p.upvotes.length
                : (p.upvotes ? Object.keys(p.upvotes).length : 0);
            return (replyCount * 2) + upvoteCount;
        };

        posts.sort((a, b) => {
            if ((b.pinned === true) !== (a.pinned === true)) {
                return (b.pinned === true) - (a.pinned === true);
            }
            if (sortBy === 'trending') {
                const scoreDiff = getEngagementScore(b) - getEngagementScore(a);
                if (scoreDiff !== 0) return scoreDiff;
                return new Date(b.createdAt) - new Date(a.createdAt);
            }
            if (sortBy === 'replies') {
                return (b.replies ? b.replies.length : 0) - (a.replies ? a.replies.length : 0);
            }
            return new Date(b.createdAt) - new Date(a.createdAt);
        });

        const resultCount = document.getElementById('resultCount');
        if (resultCount) {
            resultCount.textContent = `Showing ${posts.length} post${posts.length === 1 ? '' : 's'}`;
        }

        const statPosts = document.getElementById('statPosts');
        if (statPosts && !categoryId && !searchQuery && sortBy === 'recent') {
            statPosts.textContent = posts.length;
        }

        if (posts.length === 0) {
            feed.innerHTML = `<div class="post-card"><p style="margin:0; color:var(--muted);">No posts match the current filter.</p></div>`;
            return;
        }

        posts.forEach(post => renderPostToHTML(post));
    } catch (error) {
        console.error("Error loading feed:", error);
        feed.innerHTML = `<div class="post-card"><p style="margin:0; color:#c53030;">Could not reach ${API_BASE_URL}/posts. Check that Spring Boot is running.</p></div>`;
    }
}

// 2. Render Individual Post Card to HTML (never renders raw authorId text)
function renderPostToHTML(post) {
    const feed = document.getElementById('postsFeed');
    if (!feed) return;

    const categoryNames = {
        1: "INTERVIEW_PREP", 2: "RESUME_AND_APPLICATION", 3: "OFFER_AND_NEGOTIATION",
        4: "INDUSTRY_INSIGHT", 5: "CAREER_FAIRS_AND_EVENTS", 6: "GENERAL"
    };
    const categoryLabel = categoryNames[post.categoryId] || "GENERAL";
    const postAuthorLabel = post.authorName || "Unknown User";
    const canMarkHelpful = isCurrentUserPostCreator(post);

    const tagsHTML = (post.tags && post.tags.length > 0)
        ? `<div style="display:flex; flex-wrap:wrap; gap:6px; margin-bottom:12px;">
            ${post.tags.map(t => `<span class="category-badge">${t.tagType === 'employer' ? '🏢 ' : t.tagType === 'role' ? '💼 ' : ''}${t.name}</span>`).join('')}
           </div>`
        : '';

    const sortedReplies = [...(post.replies || [])].sort((a, b) => (b.helpful === true) - (a.helpful === true));

    const repliesHTML = sortedReplies.map(reply => {
        const replyAuthorLabel = reply.authorName || "Unknown User";
        return `
            <div class="reply-card ${reply.helpful ? 'helpful' : ''}">
                <div class="reply-header">
                    <div>
                        <strong><a href="#" class="user-link" onclick="viewUserProfile('${reply.authorId}')">${replyAuthorLabel}</a></strong> &bull; <span>${new Date(reply.createdAt).toLocaleDateString()}</span>
                        ${reply.helpful ? '<span class="helpful-badge">&#10004; Helpful Answer</span>' : ''}
                    </div>
                </div>
                <p class="reply-body">${reply.body}</p>
                <div class="reply-actions">
                    <button class="action-btn" onclick="handleReplyUpvote('${post.id}', '${reply.id}')">
                        👍 Upvote (${reply.upvotes ? reply.upvotes.length : 0})
                    </button>
                    ${canMarkHelpful ? `
                        <button class="action-btn" onclick="handleToggleReplyHelpful('${post.id}', '${reply.id}')">
                            ${reply.helpful ? '✓ Helpful' : 'Mark Helpful'}
                        </button>
                    ` : ''}
                </div>
            </div>
        `;
    }).join('');

    const postCard = document.createElement('div');
    postCard.className = `post-card ${post.pinned ? 'pinned' : ''}`;
    postCard.innerHTML = `
        ${post.pinned ? '<div style="margin-bottom: 10px;"><span class="pinned-badge">&#128204; PINNED ANNOUNCEMENT</span></div>' : ''}
        ${post.flaggedBy && post.flaggedBy.length > 0 ? `<div class="flagged-banner">⚠️ <strong>Moderation Notice:</strong> Flagged by ${post.flaggedBy.length} user(s).</div>` : ''}

        <div class="post-header">
            <span class="category-badge">${categoryLabel}</span>
            <span>Posted by <a href="#" class="user-link" onclick="viewUserProfile('${post.authorId}')">${postAuthorLabel}</a> on ${new Date(post.createdAt).toLocaleDateString()}</span>
        </div>
        <h2 class="post-title">${post.title}</h2>
        ${tagsHTML}
        <p class="post-body">${post.body}</p>

        <div class="post-actions">
            <button class="action-btn" onclick="handlePostUpvote('${post.id}')">
                👍 Upvote Post (${post.upvotes ? post.upvotes.length : 0})
            </button>
            <button class="action-btn" onclick="handleFlagPost('${post.id}')">
                🚩 Flag Post
            </button>
            ${currentUserRole === 'CAREER_STAFF' ? `
                <button class="action-btn" onclick="handleTogglePin('${post.id}', ${!post.pinned})">
                    ${post.pinned ? 'Unpin Post' : 'Pin Post'}
                </button>
            ` : ''}
        </div>

        <div class="replies-section">
            <h3>Replies (${post.replies ? post.replies.length : 0})</h3>
            <div class="replies-list">${repliesHTML}</div>
            <form class="reply-form" onsubmit="handleReplySubmit(event, '${post.id}')">
                <textarea placeholder="Write a reply..." class="reply-body-input" required></textarea>
                <button type="submit">Post Reply</button>
            </form>
        </div>
    `;

    feed.appendChild(postCard);
}

// 3. Form Submission Event Listener for New Posts
const newPostForm = document.getElementById('newPostForm');
if (newPostForm) {
    newPostForm.addEventListener('submit', async function (event) {
        event.preventDefault();
        const currentAuthor = getCurrentUser();
        const employerInput = document.getElementById('postEmployerTag');
        const roleInput = document.getElementById('postRoleTag');

        const postData = {
            authorId: currentAuthor,
            category: document.getElementById('postCategory').value,
            title: document.getElementById('postTitle').value,
            body: document.getElementById('postBody').value,
            employerTag: employerInput ? employerInput.value.trim() : "",
            roleTag: roleInput ? roleInput.value.trim() : ""
        };

        try {
            const response = await fetch(`${API_BASE_URL}/posts`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(postData)
            });
            if (response.ok) {
                newPostForm.reset();
                const authorInput = document.getElementById('postAuthor');
                if (authorInput) authorInput.value = currentAuthor;

                const modal = document.getElementById('createModal');
                if (modal) modal.classList.remove('show');

                renderFeed();
            }
        } catch (err) {
            console.error("Error creating post:", err);
        }
    });
}

// 4. Action Handlers (Replies, Upvotes, Helpful, Flags, Pins)
async function handleReplySubmit(event, postId) {
    event.preventDefault();
    const form = event.target;
    const bodyInput = form.querySelector('.reply-body-input');

    const replyData = {
        authorId: getCurrentUser(),
        body: bodyInput.value
    };

    try {
        const response = await fetch(`${API_BASE_URL}/posts/${postId}/replies`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(replyData)
        });
        if (response.ok) {
            form.reset();
            renderFeed();
        }
    } catch (err) {
        console.error("Error posting reply:", err);
    }
}

async function handlePostUpvote(postId) {
    const activeUser = getCurrentUser();
    await fetch(`${API_BASE_URL}/posts/${postId}/upvote`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: activeUser })
    });
    renderFeed();
}

async function handleFlagPost(postId) {
    const activeUser = getCurrentUser();
    const reason = prompt("Please provide a reason for flagging this post:");
    if (reason === null) return;

    try {
        const response = await fetch(`${API_BASE_URL}/posts/${postId}/flag`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                userId: activeUser,
                reason: reason.trim() !== "" ? reason : "Inappropriate content"
            })
        });

        if (response.ok) {
            renderFeed();
            alert("Post has been successfully flagged for moderation.");
        } else {
            console.error("Failed to flag post");
        }
    } catch (err) {
        console.error("Error flagging post:", err);
    }
}

async function handleTogglePin(postId, newPinStatus) {
    const activeUser = getCurrentUser();
    try {
        const response = await fetch(`${API_BASE_URL}/posts/${postId}/pin`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                userId: activeUser,
                userRole: currentUserRole,
                isPinned: newPinStatus
            })
        });
        if (response.ok) {
            renderFeed();
        } else {
            console.error("Failed to update pin status");
        }
    } catch (err) {
        console.error("Error toggling pin status:", err);
    }
}

async function handleReplyUpvote(postId, replyId) {
    const activeUser = getCurrentUser();
    try {
        const response = await fetch(`${API_BASE_URL}/posts/${postId}/replies/${replyId}/upvote`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId: activeUser })
        });
        if (response.ok) {
            renderFeed();
        } else {
            console.error("Failed to upvote reply");
        }
    } catch (err) {
        console.error("Error upvoting reply:", err);
    }
}

async function handleToggleReplyHelpful(postId, replyId) {
    try {
        const response = await fetch(
            `${API_BASE_URL}/posts/${encodeURIComponent(postId)}/replies/${encodeURIComponent(replyId)}/helpful`,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId: getCurrentUser() })
            }
        );
        if (response.ok) {
            renderFeed();
        } else if (response.status === 403) {
            alert("Only the post creator can mark replies as helpful.");
        } else {
            console.error("Failed to toggle helpful status on reply");
        }
    } catch (err) {
        console.error("Error toggling reply helpful status:", err);
    }
}

// 5. Sidebar Profile Card Sync & Privacy Toggle
async function initSidebarProfile() {
    const userId = getCurrentUser();
    const toggleEl = document.getElementById('showHistoryToggle');
    const statusEl = document.getElementById('sidebarPrivacyStatus');
    const nameEl = document.getElementById('sideProfileName');
    const avatarEl = document.getElementById('sideProfileAvatar');

    let isVisible = true;
    let resolvedUserId = userId;

    try {
        const res = await fetch(`${API_BASE_URL}/users/${encodeURIComponent(userId)}/profile`);
        if (res.ok) {
            const data = await res.json();
            if (data.userId) resolvedUserId = data.userId;

            const displayName = data.userName || data.name || userId;
            if (nameEl) nameEl.textContent = displayName;
            if (avatarEl) {
                const initials = displayName
                    .split(/\s+/)
                    .filter(Boolean)
                    .map(part => part[0])
                    .join('')
                    .slice(0, 2)
                    .toUpperCase();
                avatarEl.textContent = initials || "AU";
            }

            const isHidden = Boolean(data.hidePostHistory ?? data.hideHistory);
            isVisible = !isHidden;
        }
    } catch (err) {
        console.error("Error syncing sidebar profile:", err);
    } finally {
        if (toggleEl) {
            toggleEl.checked = isVisible;
            toggleEl.dataset.userId = resolvedUserId;
            toggleEl.disabled = false;
        }
        if (statusEl) {
            statusEl.textContent = isVisible
                ? "Your posting history is visible on your public profile."
                : "Your posting history is hidden from your public profile. You can still post and reply.";
        }
    }
}

async function handleSidebarHistoryToggle(event) {
    const checkbox = event.target;
    const showHistory = checkbox.checked;
    const hidePostHistory = !showHistory;
    const userId = checkbox.dataset.userId || getCurrentUser();
    const statusEl = document.getElementById('sidebarPrivacyStatus');

    checkbox.disabled = true;
    try {
        const res = await fetch(`${API_BASE_URL}/users/${encodeURIComponent(userId)}/privacy`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ hidePostHistory, hideHistory: hidePostHistory })
        });
        if (!res.ok) throw new Error("Failed to update privacy setting");

        const updated = await res.json();
        if (updated.userId) {
            checkbox.dataset.userId = updated.userId;
        }

        if (statusEl) {
            statusEl.textContent = showHistory
                ? "Your posting history is visible on your public profile."
                : "Your posting history is hidden from your public profile. You can still post and reply.";
        }
    } catch (err) {
        console.error("Error saving privacy toggle:", err);
        checkbox.checked = !showHistory;
    } finally {
        checkbox.disabled = false;
    }
}

// 6. User Profile Page Renderer & Privacy Toggle Handler
async function renderProfilePage(targetUserId, viewerId) {
    const profileContent = document.getElementById('profileContent');
    if (!profileContent) return;

    try {
        const response = await fetch(`${API_BASE_URL}/users/${encodeURIComponent(targetUserId)}/profile`);
        if (!response.ok) throw new Error("Failed to fetch profile");
        const profileData = await response.json();

        const userPosts = profileData.posts || [];
        const userReplies = profileData.replies || [];
        const isHidden = Boolean(profileData.hidePostHistory ?? profileData.hideHistory);

        const displayName = profileData.userName || profileData.name || (userPosts[0] && userPosts[0].authorName) || "User Profile";
        const titleEl = document.getElementById('profileTitle');
        if (titleEl) titleEl.innerText = displayName;

        const canEditPrivacy = Boolean(
            viewerId && (
                viewerId.toLowerCase() === targetUserId.toLowerCase() ||
                (profileData.userName && viewerId.toLowerCase() === profileData.userName.toLowerCase()) ||
                (profileData.userId && viewerId.toLowerCase() === profileData.userId.toLowerCase())
            )
        );

        const toggleEl = document.getElementById('hideHistoryToggle');
        if (toggleEl) {
            toggleEl.checked = isHidden;
            toggleEl.dataset.targetUserId = profileData.userId || targetUserId;
            toggleEl.disabled = !canEditPrivacy;
        }

        const statusText = document.getElementById('privacyStatusText');
        if (statusText) {
            if (!canEditPrivacy) {
                statusText.innerText = isHidden
                    ? "This user has hidden their posting history."
                    : "This user's posting history is visible on their public profile.";
            } else {
                statusText.innerText = isHidden
                    ? "Posting history is hidden from the public profile. Posting and replying remain enabled."
                    : "Your created posts and replies are visible on your profile.";
            }
        }

        if (isHidden) {
            profileContent.innerHTML = `
                <p style="color: var(--muted); font-style: italic;">
                    This user has chosen to hide their posting history.
                </p>
            `;
            return;
        }

        profileContent.innerHTML = `
            <h3>Posts Created (${userPosts.length})</h3>
            <div style="margin-top: 10px;">
                ${userPosts.length > 0 ? userPosts.map(p => `
                    <div class="profile-post-card">
                        <h4 class="profile-post-title">${p.title}</h4>
                        <div style="font-size: 0.75em; color: #666; margin-bottom: 6px;">Posted on ${new Date(p.createdAt).toLocaleDateString()}</div>
                        <p class="profile-post-body">${p.body}</p>
                    </div>
                `).join('') : '<p style="color:#666; font-style:italic;">No posts created yet.</p>'}
            </div>

            <h3 style="margin-top: 25px;">Replies Made (${userReplies.length})</h3>
            <ul>
                ${userReplies.length > 0 ? userReplies.map(r => `<li class="reply-history-item">On <em>${r.postTitle}</em>: "${r.replyBody}" (${new Date(r.createdAt).toLocaleDateString()})</li>`).join('') : '<li style="color:#666; font-style:italic;">No replies made yet.</li>'}
            </ul>
        `;
    } catch (err) {
        console.error("Error loading profile:", err);
        profileContent.innerHTML = `<p style="color: #c53030;">Could not load profile data.</p>`;
    }
}

async function handlePrivacyToggle(event) {
    const checkbox = event.target;
    const hidePostHistory = checkbox.checked;
    const targetUserId = checkbox.dataset.targetUserId;
    if (!targetUserId) return;

    checkbox.disabled = true;
    try {
        const response = await fetch(`${API_BASE_URL}/users/${encodeURIComponent(targetUserId)}/privacy`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ hidePostHistory, hideHistory: hidePostHistory })
        });
        if (!response.ok) throw new Error("Failed to save privacy preference");
        await renderProfilePage(targetUserId, getCurrentUser());
    } catch (err) {
        console.error("Error updating privacy preference:", err);
        checkbox.checked = !hidePostHistory;
        checkbox.disabled = false;
    }
}

// 7. Wire UI Controls on Page Load
window.addEventListener('DOMContentLoaded', () => {
    const authorInput = document.getElementById('postAuthor');
    if (authorInput) {
        if (!authorInput.value.trim()) {
            authorInput.value = "AlexUser";
        }
        authorInput.readOnly = true;
    }

    initSidebarProfile();

    const searchInput = document.getElementById('searchInput');
    const clearSearchBtn = document.getElementById('clearSearch');
    const sortSelect = document.getElementById('sortSelect');
    const categoryTabs = document.querySelectorAll('#categoryTabs .tab');

    categoryTabs.forEach(tab => {
        tab.addEventListener('click', () => {
            categoryTabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            renderFeed(tab.dataset.category || '', activeSearch, activeSort);
        });
    });

    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            renderFeed(activeCategory, e.target.value.trim(), activeSort);
        });
    }

    if (clearSearchBtn) {
        clearSearchBtn.addEventListener('click', () => {
            if (searchInput) searchInput.value = '';
            categoryTabs.forEach(t => t.classList.remove('active'));
            const allTab = document.querySelector('#categoryTabs .tab[data-category=""]');
            if (allTab) allTab.classList.add('active');
            renderFeed('', '', activeSort);
        });
    }

    if (sortSelect) {
        sortSelect.addEventListener('change', (e) => {
            renderFeed(activeCategory, activeSearch, e.target.value);
        });
    }

    renderFeed();
});