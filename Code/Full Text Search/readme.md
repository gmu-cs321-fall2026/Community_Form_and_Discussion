# CareerConnect Student Forum Demo

A small Java + HTML demo for a student career forum user story.

## Features
- Search full post titles and bodies by keyword.
- Category tabs: Interview Prep, Resume & Applications, Offer & Negotiation, Industry Insight, Career Fairs & Events, General.
- Combine a category filter with text search; clear/reset filters.
- Sort by recent or most discussed.
- Preview author profiles and demonstrate opt-out public posting history (posts and replies remain available in the forum even when history is hidden).
- Create a sample post to demonstrate category selection and posting.
- Responsive layout with seeded demo posts.

## Run in VS Code
1. Extract the ZIP and open the `career_forum_demo` folder in VS Code.
2. Open `src/main/java/CareerForumDemoServer.java`.
3. Run the `main` method. Requires a JDK (Java 17+ recommended).
4. Visit http://localhost:8080.
5. Keep the terminal open while using the demo; press Ctrl+C to stop it.

If port 8080 is busy, stop the older server or run with a VM argument `-Dport=8081`, then open http://localhost:8081.

## Demo limitations
This is a front-end prototype. Search/filter/profile state and newly created posts live in browser memory and reset on refresh. It does not use a database or real authentication. The profile toggle demonstrates the opt-out requirement in the UI; production code must enforce the privacy setting on the server and apply it consistently to public profile APIs.
