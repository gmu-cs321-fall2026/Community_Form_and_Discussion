/**
 * Represents a single reply submitted to a main post.
 */
class Reply {
    /**
     * Constructs a Reply instance.
     * @param {Object} params
     * @param {string} params.postId - Unique identifier of the parent post.
     * @param {string} params.authorId - Username/ID of the commenter.
     * @param {string} params.body - Text content of the reply.
     */
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
    }
}

/**
 * Represents a primary forum discussion post.
 */
class Post {
    /**
     * Constructs a Post instance.
     * @param {Object} params
     * @param {string} params.title - Title (1 to 120 characters).
     * @param {string} params.body - Main body content.
     * @param {string} params.authorId - User ID of the post author.
     * @param {string} params.category - Category badge text.
     * @param {Object.<string, string>} [params.tags={}] - Map of initial key-value tags.
     */
    constructor({ title, body, authorId, category, tags = {} }) {
        // Validation: Title 1 to 120 characters
        if (!title || title.trim().length === 0 || title.trim().length > 120) {
            throw new Error("Validation Error: Title must be between 1 and 120 characters.");
        }
        // Validation: Body non-empty
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
    }

    /**
     * Adds a normalized key-value tag pair to the post.
     * @param {string} key - Tag name.
     * @param {string} value - Tag value.
     */
    addTag(key, value) {
        if (key && value) {
            this.tags[key.toUpperCase().trim()] = value.trim();
        }
    }

    /**
     * Appends a new reply to this post's reply collection.
     * @param {Object} replyData
     * @param {string} replyData.authorId - Username/ID submitting reply.
     * @param {string} replyData.body - Text of reply.
     * @returns {Reply} The newly instantiated Reply object.
     */
    addReply({ authorId, body }) {
        const reply = new Reply({ postId: this.id, authorId, body });
        this.replies.push(reply);
        return reply;
    }
}

/**
 * Converts a Post object into dynamic HTML markup and appends it to the DOM feed container.
 * @param {Post} post - The post instance to render.
 */
function renderPostToHTML(post) {
    const feed = document.getElementById('postsFeed');

    // Build the tags HTML dynamically from key-value pairs
    const tagsHTML = Object.entries(post.tags)
        .map(([key, val]) => `<span class="tag-pill"><strong>${key}:</strong> ${val}</span>`)
        .join('');

    // Construct the list of replies in HTML
    const repliesHTML = post.replies.map(reply => `
        <div class="reply-card">
            <div class="reply-header">
                <strong>${reply.authorId}</strong> &bull; <span>${reply.createdAt}</span>
            </div>
            <p class="reply-body">${reply.body}</p>
        </div>
    `).join('');

    // Construct full post card UI including reply section and submit form
    const postCard = document.createElement('div');
    postCard.className = 'post-card';
    postCard.innerHTML = `
        <div class="post-header">
            <span class="category-badge">${post.category}</span>
            <span>Posted by ${post.authorId} on ${post.createdAt}</span>
        </div>
        <h2 class="post-title">${post.title}</h2>
        <p class="post-body">${post.body}</p>
        <div class="tags-container">
            ${tagsHTML}
        </div>
        
        <!-- Reply Section -->
        <div class="replies-section">
            <h3>Replies (${post.replies.length})</h3>
            <div class="replies-list">
                ${repliesHTML}
            </div>
            <form class="reply-form" onsubmit="handleReplySubmit(event, '${post.id}')">
                <input type="text" placeholder="Your Username" class="reply-author-input" required />
                <textarea placeholder="Write a reply..." class="reply-body-input" required></textarea>
                <button type="submit">Post Reply</button>
            </form>
        </div>
    `;

    // Insert completed post into DOM
    feed.appendChild(postCard);
}

/**
 * Global map acting as an in-memory database of posts for UI state tracking.
 * @type {Map<string, Post>}
 */
const postsMap = new Map();

/**
 * Event handler for submission of the interactive reply form on a post card.
 * @param {Event} event - The form submit event.
 * @param {string} postId - Target post unique identifier.
 */
function handleReplySubmit(event, postId) {
    event.preventDefault();
    const form = event.target;
    const authorInput = form.querySelector('.reply-author-input');
    const bodyInput = form.querySelector('.reply-body-input');

    const post = postsMap.get(postId);
    if (post) {
        // Add new reply to post instance
        post.addReply({
            authorId: authorInput.value,
            body: bodyInput.value
        });

        // Re-render the updated feed view
        document.getElementById('postsFeed').innerHTML = '';
        postsMap.forEach(p => renderPostToHTML(p));
    }
}

// --- INITIAL DEMO SETUP & EXECUTION ---

const post1 = new Post({
    title: "Interview Questions",
    body: "Body",
    authorId: "user",
    category: "CAREER_ADVICE",
    tags: {
        "EMPLOYER": "EMPLOYER",
        "ROLE": "ROLE"
    }
});
post1.addTag("LOCATION", "LOCATION");
post1.addReply({ authorId: "Alex", body: "Focus on System Design and Data Structures!" });

const post2 = new Post({
    title: "TITLE",
    body: "BODY",
    authorId: "ADMIN",
    category: "ANNOUNCEMENT"
});

// Register posts in global storage
postsMap.set(post1.id, post1);
postsMap.set(post2.id, post2);

// Initial render to page
postsMap.forEach(post => renderPostToHTML(post));