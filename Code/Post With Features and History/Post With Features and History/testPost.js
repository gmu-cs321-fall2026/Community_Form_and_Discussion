class Reply {
    constructor({ postId, authorId, body }) {
        if (!postId) {
            throw new Error("Validation Error: Reply must be linked to a postId.");
        }
        if (!body || body.trim().length === 0) {
            throw new Error("Validation Error: Reply body cannot be empty.");
        }

        this.id = crypto.randomUUID();
        this.postId = postId;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = new Date().toLocaleDateString();
        this.upvotes = new Set();
        this.isHelpful = false;
    }

    toggleUpvote(userId) {
        if (this.upvotes.has(userId)) {
            this.upvotes.delete(userId);
        } else {
            this.upvotes.add(userId);
        }
    }

    toggleHelpful(actorId, postAuthorId) {
        if (actorId !== postAuthorId) {
            throw new Error("Only the post creator can mark a reply as helpful.");
        }
        this.isHelpful = !this.isHelpful;
    }
}

class Post {
    constructor({ title, body, authorId, category, tags = {} }) {
        if (!title || title.trim().length === 0 || title.trim().length > 120) {
            throw new Error("Validation Error: Title must be between 1 and 120 characters.");
        }
        if (!body || body.trim().length === 0) {
            throw new Error("Validation Error: Body cannot be empty.");
        }

        this.id = crypto.randomUUID();
        this.title = title;
        this.body = body;
        this.authorId = authorId;
        this.category = category;
        this.tags = tags;
        this.createdAt = new Date().toLocaleDateString();
        this.replies = [];
        
        this.upvotes = new Set();
        this.isPinned = false;
        this.flaggedBy = new Set();
    }

    toggleUpvote(userId) {
        if (this.upvotes.has(userId)) {
            this.upvotes.delete(userId);
        } else {
            this.upvotes.add(userId);
        }
    }

    setPinned(isPinned, userRole) {
        if (userRole !== 'CAREER_STAFF') {
            throw new Error("Only Career Services Staff can pin or unpin posts.");
        }
        this.isPinned = isPinned;
    }

    flagPost(userId) {
        this.flaggedBy.add(userId);
    }

    addTag(key, value) {
        if (key && value) {
            this.tags[key.toUpperCase().trim()] = value.trim();
        }
    }

    addReply({ authorId, body }) {
        const reply = new Reply({ postId: this.id, authorId, body });
        this.replies.push(reply);
        return reply;
    }
}

const postsMap = new Map();
const userProfilesMap = new Map(); // Privacy tracking { showHistory: boolean }

const currentLoggedInUser = "AlexUser";
const currentUserRole = "CAREER_STAFF"; 

function getUserProfile(userId) {
    if (!userProfilesMap.has(userId)) {
        userProfilesMap.set(userId, { showHistory: true }); // Opt-out privacy model: true by default
    }
    return userProfilesMap.get(userId);
}

function toggleUserHistoryPrivacy(userId, viewerId) {
    const profile = getUserProfile(userId);
    profile.showHistory = !profile.showHistory;
    
    if (typeof renderProfilePage === 'function' && document.getElementById('profileContent')) {
        renderProfilePage(userId, viewerId);
    }
}

function viewUserProfile(targetUserId) {
    window.location.href = `profile.html?user=${encodeURIComponent(targetUserId)}`;
}

function renderProfilePage(targetUserId, viewerId) {
    const profile = getUserProfile(targetUserId);
    const isSelf = targetUserId === viewerId;
    const canSeeHistory = isSelf || profile.showHistory;

    const titleEl = document.getElementById('profileTitle');
    if (titleEl) titleEl.innerText = `User Profile: ${targetUserId}`;

    const privacySection = document.getElementById('privacySection');
    const profileContent = document.getElementById('profileContent');

    if (!profileContent) return;

    if (isSelf && privacySection) {
        privacySection.innerHTML = `
            <div class="privacy-box">
                <label style="cursor:pointer; font-size: 0.9em; font-weight: bold;">
                    <input type="checkbox" ${profile.showHistory ? 'checked' : ''} onchange="toggleUserHistoryPrivacy('${targetUserId}', '${viewerId}')" />
                    Show my posting history on my public profile
                </label>
            </div>
        `;
    } else if (privacySection) {
        privacySection.innerHTML = '';
    }

    if (canSeeHistory) {
        const userPosts = Array.from(postsMap.values()).filter(p => p.authorId === targetUserId);
        const userReplies = [];
        postsMap.forEach(p => {
            p.replies.forEach(r => {
                if (r.authorId === targetUserId) {
                    userReplies.push({ postTitle: p.title, postId: p.id, body: r.body, date: r.createdAt });
                }
            });
        });

        profileContent.innerHTML = `
            <h3>Posts Created (${userPosts.length})</h3>
            <div style="margin-top: 10px;">
                ${userPosts.length > 0 ? userPosts.map(p => `
                    <div class="profile-post-card">
                        <h4 class="profile-post-title">${p.title}</h4>
                        <div style="font-size: 0.75em; color: #666; margin-bottom: 6px;">
                            Category: <strong>${p.category}</strong> &bull; Posted on${p.createdAt}
                        </div>
                        <p class="profile-post-body">${p.body}</p>
                    </div>
                `).join('') : '<p style="color:#666; font-style:italic;">No posts created yet.</p>'}
            </div>

            <h3 style="margin-top: 25px;">Replies Made (${userReplies.length})</h3>
            <ul>
                ${userReplies.length > 0 ? userReplies.map(r => `<li>On <em>${r.postTitle}</em>: "${r.body}" (${r.date})</li>`).join('') : '<li style="color:#666; font-style:italic;">No replies made yet.</li>'}
            </ul>
        `;
    } else {
        profileContent.innerHTML = `
            <div class="hidden-message">
                <p>🔒 This user has chosen to hide their posting history from public profiles.</p>
            </div>
        `;
    }
}

function renderFeed() {
    const feed = document.getElementById('postsFeed');
    if (!feed) return;
    feed.innerHTML = '';

    const sortedPosts = Array.from(postsMap.values()).sort((a, b) => {
        return (b.isPinned === true) - (a.isPinned === true);
    });

    sortedPosts.forEach(post => renderPostToHTML(post));
}

function renderPostToHTML(post) {
    const feed = document.getElementById('postsFeed');
    if (!feed) return;

    const tagsHTML = Object.entries(post.tags)
        .map(([key, val]) => `<span class="tag-pill"><strong>${key}:</strong> ${val}</span>`)
        .join('');

    const repliesHTML = post.replies.map(reply => {
        const canMarkHelpful = currentLoggedInUser === post.authorId;
        return `
            <div class="reply-card ${reply.isHelpful ? 'helpful' : ''}">
                <div class="reply-header">
                    <div>
                        <strong><a href="#" class="user-link" onclick="viewUserProfile('${reply.authorId}')">${reply.authorId}</a></strong> &bull; <span>${reply.createdAt}</span>
                        ${reply.isHelpful ? '<span class="helpful-badge">&#10004; Helpful Answer</span>' : ''}
                    </div>
                </div>
                <p class="reply-body">${reply.body}</p>
                <div class="reply-actions">
                    <button class="action-btn" onclick="handleReplyUpvote('${post.id}', '${reply.id}')">
                        👍 Upvote (${reply.upvotes.size})
                    </button>
                    ${canMarkHelpful ? `
                        <button class="action-btn" onclick="handleToggleHelpful('${post.id}', '${reply.id}')">
                            ${reply.isHelpful ? 'Unmark Helpful' : 'Mark as Helpful'}
                        </button>
                    ` : ''}
                </div>
            </div>
        `;
    }).join('');

    const postCard = document.createElement('div');
    postCard.className = `post-card ${post.isPinned ? 'pinned' : ''}`;
    postCard.innerHTML = `
        ${post.isPinned ? '<div style="margin-bottom: 10px;"><span class="pinned-badge">&#128204; PINNED ANNOUNCEMENT</span></div>' : ''}
        ${post.flaggedBy.size > 0 ? `<div class="flagged-banner">⚠️ <strong>Moderation Notice:</strong> This post has been flagged by ${post.flaggedBy.size} user(s).</div>` : ''}

        <div class="post-header">
            <span class="category-badge">${post.category}</span>
            <span>Posted by <a href="#" class="user-link" onclick="viewUserProfile('${post.authorId}')">${post.authorId}</a> on ${post.createdAt}</span>
        </div>
        <h2 class="post-title">${post.title}</h2>
        <p class="post-body">${post.body}</p>
        <div class="tags-container">${tagsHTML}</div>

        <div class="post-actions">
            <button class="action-btn" onclick="handlePostUpvote('${post.id}')">
                👍 Upvote Post (${post.upvotes.size})
            </button>
            <button class="action-btn" onclick="handleFlagPost('${post.id}')">
                🚩 Flag Post
            </button>
            ${currentUserRole === 'CAREER_STAFF' ? `
                <button class="action-btn" onclick="handleTogglePin('${post.id}')">
                    ${post.isPinned ? 'Unpin Post' : 'Pin Post'}
                </button>
            ` : ''}
        </div>
        
        <div class="replies-section">
            <h3>Replies (${post.replies.length})</h3>
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

function handlePostUpvote(postId) {
    const post = postsMap.get(postId);
    if (post) {
        post.toggleUpvote(currentLoggedInUser);
        renderFeed();
    }
}

function handleReplyUpvote(postId, replyId) {
    const post = postsMap.get(postId);
    if (post) {
        const reply = post.replies.find(r => r.id === replyId);
        if (reply) {
            reply.toggleUpvote(currentLoggedInUser);
            renderFeed();
        }
    }
}

function handleToggleHelpful(postId, replyId) {
    const post = postsMap.get(postId);
    if (post) {
        const reply = post.replies.find(r => r.id === replyId);
        if (reply) {
            try {
                reply.toggleHelpful(currentLoggedInUser, post.authorId);
                renderFeed();
            } catch (err) {
                alert(err.message);
            }
        }
    }
}

function handleFlagPost(postId) {
    const post = postsMap.get(postId);
    if (post) {
        post.flagPost(currentLoggedInUser);
        alert("Post has been flagged for moderation review.");
        renderFeed();
    }
}

function handleTogglePin(postId) {
    const post = postsMap.get(postId);
    if (post) {
        try {
            post.setPinned(!post.isPinned, currentUserRole);
            renderFeed();
        } catch (err) {
            alert(err.message);
        }
    }
}

function handleReplySubmit(event, postId) {
    event.preventDefault();
    const form = event.target;
    const authorInput = form.querySelector('.reply-author-input');
    const bodyInput = form.querySelector('.reply-body-input');

    const post = postsMap.get(postId);
    if (post) {
        post.addReply({
            authorId: authorInput.value,
            body: bodyInput.value
        });
        renderFeed();
    }
}

const newPostForm = document.getElementById('newPostForm');
if (newPostForm) {
    newPostForm.addEventListener('submit', function (event) {
        event.preventDefault();

        const author = document.getElementById('postAuthor').value;
        const category = document.getElementById('postCategory').value;
        const title = document.getElementById('postTitle').value;
        const body = document.getElementById('postBody').value;

        const newPost = new Post({
            title: title,
            body: body,
            authorId: author,
            category: category
        });

        postsMap.set(newPost.id, newPost);
        renderFeed();
        newPostForm.reset();
    });
}

// --- INITIAL DEMO SETUP ---

const post1 = new Post({
    title: "Fall Career Fair Schedule & Tips",
    body: "Please review the attached guidelines for preparing your resume and attire before attending the career fair.",
    authorId: "AlexUser",
    category: "CAREER_FAIRS_AND_EVENTS",
    tags: { "LOCATION": "Student Union", "DATE": "Oct 25" }
});
post1.setPinned(true, "CAREER_STAFF");
post1.addReply({ authorId: "Sarah_Recruiter", body: "Make sure to bring at least 10 printed copies of your resume!" });

const post2 = new Post({
    title: "System Design Interview Prep Questions",
    body: "What are the best resources to practice scalable architectures?",
    authorId: "JobSeeker99",
    category: "INTERVIEW_PREP"
});
post2.flagPost("UserXYZ");

postsMap.set(post1.id, post1);
postsMap.set(post2.id, post2);

renderFeed();