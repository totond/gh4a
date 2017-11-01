package com.gh4a.activities;

import android.content.Context;
import android.content.Intent;

import com.gh4a.Gh4Application;
import com.gh4a.utils.ApiHelpers;
import com.meisolsson.githubsdk.model.GitHubCommentBase;
import com.meisolsson.githubsdk.model.request.CommentRequest;
import com.meisolsson.githubsdk.model.request.repository.CreateCommitComment;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommentService;

import io.reactivex.Single;

public class EditCommitCommentActivity extends EditCommentActivity {
    public static Intent makeIntent(Context context, String repoOwner, String repoName,
            String commitSha, long id, String body) {
        Intent intent = new Intent(context, EditCommitCommentActivity.class)
                .putExtra("commit", commitSha);
        return EditCommentActivity.fillInIntent(intent, repoOwner, repoName, id, 0L, body, 0);
    }

    @Override
    protected Single<GitHubCommentBase> createComment(String repoOwner, String repoName,
            String body, long replyToCommentId) {
        RepositoryCommentService service =
                Gh4Application.get().getGitHubService(RepositoryCommentService.class);
        CreateCommitComment request = CreateCommitComment.builder().body(body).build();
        String sha = getIntent().getStringExtra("commit");
        return service.createCommitComment(repoOwner, repoName, sha, request)
                .compose(response -> ApiHelpers.throwOnFailure(response))
                .map(response -> response);
    }

    @Override
    protected Single<GitHubCommentBase> editComment(String repoOwner, String repoName,
            long commentId, String body) {
        RepositoryCommentService service =
                Gh4Application.get().getGitHubService(RepositoryCommentService.class);
        CommentRequest request = CommentRequest.builder().body(body).build();
        return service.editCommitComment(repoOwner, repoName, commentId, request)
                .compose(response -> ApiHelpers.throwOnFailure(response))
                .map(response -> response);
    }
}