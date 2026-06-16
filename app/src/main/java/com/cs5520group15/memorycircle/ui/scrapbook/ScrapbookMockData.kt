package com.cs5520group15.memorycircle.ui.scrapbook

/**
 * What: One photo on a scrapbook post — the file's URL plus who uploaded it and
 *       their one-line description. Photos live as a list field directly on the
 *       post document (no subcollection).
 * Who: Used by ScrapbookPost/ScrapbookEntry, ScrapbookRepository, and the screens.
 * When: Created when a member adds a photo to a post.
 */
data class Photo(
    val photoId:     String,
    val url:         String,   // remote URL (picsum placeholder until Storage exists)
    val storagePath: String,   // Firebase Storage path — empty until Storage is wired up
    val description: String,   // this photo's caption
    val uploaderId:  String    // uid of whoever uploaded it
)

/**
 * What: A single member's contribution as the UI renders it — an avatar/name plus a
 *       photo and caption. This is now a *view model* derived from a post's photos
 *       (see ScrapbookEntry.contributions); it is no longer stored in Firestore.
 * Who: Used by the scrapbook screens (ContributionBlock / HistoryContributionBlock).
 * When: Built on the fly from each post's photos for display.
 */
data class MemberContribution(
    val memberName:  String,        // drives avatar initial + label
    val photoUri:    String,        // remote URL
    val description: String,        // this photo's caption
    val uploaderId:  String = ""    // uid of whoever uploaded it — drives "is this mine?"
)

/**
 * What: A single memory "post" in a group's monthly scrapbook. The author sets the
 *       title + tags and adds one or more photos; other members can append their own
 *       photos. Comments live in a subcollection; commentCount mirrors its size.
 *       Kept named ScrapbookEntry so existing screens need no changes.
 * Who: Used by ScrapbookRepository and the scrapbook screens.
 * When: Instantiated when loading or creating a post.
 */
data class ScrapbookEntry(
    val id:           String,                  // postId
    val authorId:     String = "",
    val authorName:   String = "",
    val title:        String = "",
    val date:         String = "",             // day label, e.g. "June 1"
    val tags:         List<String> = emptyList(),
    val photos:       List<Photo> = emptyList(),
    val comments:     List<Comment> = emptyList(),
    val commentCount: Int = 0
) {
    /**
     * The post's photos presented as per-member contributions, so the existing
     * timeline UI (which renders entry.contributions) keeps working unchanged.
     */
    val contributions: List<MemberContribution>
        get() = photos.map { photo ->
            MemberContribution(
                memberName  = authorName,
                photoUri    = photo.url,
                description = photo.description,
                uploaderId  = photo.uploaderId
            )
        }
}

/**
 * What: A short comment a group member leaves on a memory post.
 * Who: Used by ScrapbookEntry, ScrapbookRepository, and the scrapbook screens.
 * When: Created when a member posts a comment.
 */
data class Comment(
    val id:     String,
    val author: String,
    val text:   String
)
