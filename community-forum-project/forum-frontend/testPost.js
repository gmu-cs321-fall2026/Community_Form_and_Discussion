// Helper function to grab the active user dynamically from the author input
function getCurrentUser() {
    const authorInput = document.getElementById('postAuthor');
    return authorInput && authorInput.value.trim() ? authorInput.value.trim() : "AnonymousUser";
}

const currentUserRole = "CAREER_STAFF"; 

function viewUserProfile(targetUserId) {
    window.location.href = `profile.html?user=${encodeURIComponent(targetUserId)}`;
}

// 1. Render Feed from Spring Boot Database
async function renderFeed() {
    try {
        const response = await fetch('http://localhost:8080/api/posts');
        if (!response.ok) throw new Error("Failed to fetch posts");
        
        const posts = await response.json();
        const feed = document.getElementById('postsFeed');
        if (!feed) return;
        feed.innerHTML = '';

        // Sort posts so pinned items appear at the top
        posts.sort((a, b) => (b.pinned === true) - (a.pinned === true));
        posts.forEach(post => renderPostToHTML(post));
    } catch (error) {
        console.error("Error loading feed:", error);
    }
}

// 2. Render Individual Post Card to HTML
function renderPostToHTML(post) {
    const feed = document.getElementById('postsFeed');
    if (!feed) return;

    const categoryNames = {
        1: "INTERVIEW_PREP", 2: "RESUME_AND_APPLICATION", 3: "OFFER_AND_NEGOTIATION",
        4: "INDUSTRY_INSIGHT", 5: "CAREER_FAIRS_AND_EVENTS", 6: "GENERAL"
    };
    const categoryLabel = categoryNames[post.categoryId] || "GENERAL";

    const repliesHTML = (post.replies || []).map(reply => {
        return `
            <div class="reply-card ${reply.helpful ? 'helpful' : ''}">
                <div class="reply-header">
                    <div>
                        <strong><a href="#" class="user-link" onclick="viewUserProfile('${reply.authorId}')">${reply.authorId}</a></strong> &bull; <span>${new Date(reply.createdAt).toLocaleDateString()}</span>
                        ${reply.helpful ? '<span class="helpful-badge">&#10004; Helpful Answer</span>' : ''}
                    </div>
                </div>
                <p class="reply-body">${reply.body}</p>
                <div class="reply-actions">
                    <button class="action-btn" onclick="handleReplyUpvote('${post.id}', '${reply.id}')">
                        👍 Upvote (${reply.upvotes ? reply.upvotes.length : 0})
                    </button>
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
            <span>Posted by <a href="#" class="user-link" onclick="viewUserProfile('${post.authorId}')">${post.authorId}</a> on ${new Date(post.createdAt).toLocaleDateString()}</span>
        </div>
        <h2 class="post-title">${post.title}</h2>
        <p class="post-body">${post.body}</p>

        <div class="post-actions">
            <button class="action-btn" onclick="handlePostUpvote('${post.id}')">
                👍 Upvote Post (${post.upvotes ? post.upvotes.length : 0})
            </button>
            <button class="action-btn" onclick="handleFlagPost('${post.id}')">
                🚩 Flag Post
            </button>
            ${currentUserRole === 'CAREER_STAFF' ? `
                <button class="action-btn" onclick="handleTogglePin('${post.id}', ${!post.pinned})"">
                    ${post.pinned ? 'Unpin Post' : 'Pin Post'}
                </button>
            ` : ''}
        </div>
        
        <div class="replies-section">
            <h3>Replies (${post.replies ? post.replies.length : 0})</h3>
            <div class="replies-list">${repliesHTML}</div>
            <form class="reply-form" onsubmit="handleReplySubmit(event, '${post.id}')">
                <input type="text" placeholder="Your Username" class="reply-author-input" required />
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
        const postData = {
            authorId: document.getElementById('postAuthor').value,
            category: document.getElementById('postCategory').value,
            title: document.getElementById('postTitle').value,
            body: document.getElementById('postBody').value
        };

        try {
            const response = await fetch('http://localhost:8080/api/posts', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(postData)
            });
            if (response.ok) {
                newPostForm.reset();
                renderFeed();
            }
        } catch (err) {
            console.error("Error creating post:", err);
        }
    });
}

// 4. Action Handlers (Replies, Upvotes, Flags, Pins)
async function handleReplySubmit(event, postId) {
    event.preventDefault();
    const form = event.target;
    const authorInput = form.querySelector('.reply-author-input');
    const bodyInput = form.querySelector('.reply-body-input');

    const replyData = { authorId: authorInput.value, body: bodyInput.value };

    try {
        const response = await fetch(`http://localhost:8080/api/posts/${postId}/replies`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(replyData)
        });
        if (response.ok) renderFeed();
    } catch (err) {
        console.error("Error posting reply:", err);
    }
}

async function handlePostUpvote(postId) {
    const activeUser = getCurrentUser();
    await fetch(`http://localhost:8080/api/posts/${postId}/upvote`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: activeUser })
    });
    renderFeed();
}

async function handleFlagPost(postId) {
    const activeUser = getCurrentUser();
    const reason = prompt("Please provide a reason for flagging this post:");
    
    // If the user cancels the prompt, stop execution
    if (reason === null) return; 

    try {
        const response = await fetch(`http://localhost:8080/api/posts/${postId}/flag`, {
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
        const response = await fetch(`http://localhost:8080/api/posts/${postId}/pin`, {
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

// 5. User Profile Page Renderer
async function renderProfilePage(targetUserId, viewerId) {
    const titleEl = document.getElementById('profileTitle');
    if (titleEl) titleEl.innerText = `User Profile: ${targetUserId}`;

    const profileContent = document.getElementById('profileContent');
    if (!profileContent) return;

    try {
        const response = await fetch(`http://localhost:8080/api/users/${targetUserId}/profile`);
        const profileData = await response.json();
        const userPosts = profileData.posts || [];
        const userReplies = profileData.replies || [];

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
                ${userReplies.length > 0 ? userReplies.map(r => `<li>On <em>${r.postTitle}</em>: "${r.replyBody}" (${new Date(r.createdAt).toLocaleDateString()})</li>`).join('') : '<li style="color:#666; font-style:italic;">No replies made yet.</li>'}
            </ul>
        `;
    } catch (err) {
        console.error("Error loading profile:", err);
    }
}
async function handleReplyUpvote(postId, replyId) {
        const activeUser = getCurrentUser();
        try {
            const response = await fetch(`http://localhost:8080/api/posts/${postId}/replies/${replyId}/upvote`, {
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
let currentUser = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";

function getCurrentUser() {
    return currentUser;
}
// Automatically trigger feed rendering when page opens
window.addEventListener('DOMContentLoaded', renderFeed);