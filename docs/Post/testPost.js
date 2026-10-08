class Post {
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
    }

    addTag(key, value) {
        if (key && value) {
            this.tags[key.toUpperCase().trim()] = value.trim();
        }
    }
}

/**
 * Converts a Post object into HTML elements and renders it onto the page.
 */
function renderPostToHTML(post) {
    const feed = document.getElementById('postsFeed');

    // Build the tags HTML dynamically from the Map/Object
    const tagsHTML = Object.entries(post.tags)
        .map(([key, val]) => `<span class="tag-pill"><strong>${key}:</strong> ${val}</span>`)
        .join('');

    // Construct post element template
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
    `;

    // Insert into webpage DOM
    feed.appendChild(postCard);
}

// --- CREATE AND RENDER POSTS ---

const post1 = new Post({
    title: "TITLE",
    body: "Body",
    authorId: "user",
    category: "CAREER_ADVICE",
    tags: {
        "EMPLOYER": "EMPLOYER",
        "ROLE": "ROLE"
    }
});
post1.addTag("LOCATION", "LOCATION");

const post2 = new Post({
    title: "TITLE",
    body: "BODY",
    authorId: "ADMIN",
    category: "ANNOUNCEMENT"
});

// Render posts directly to the webpage view
renderPostToHTML(post1);
renderPostToHTML(post2);