/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.Loader;

import com.gh4a.Gh4Application;
import com.gh4a.loader.LoaderResult;
import com.gh4a.loader.PullRequestCommentsLoader;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.IntentUtils;
import com.gh4a.widget.ReactionBar;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.PositionalCommentBase;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.request.ReactionRequest;
import com.meisolsson.githubsdk.service.reactions.ReactionService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewCommentService;

import java.io.IOException;
import java.util.List;

public class PullRequestDiffViewerActivity extends DiffViewerActivity<ReviewComment> {
    public static Intent makeIntent(Context context, String repoOwner, String repoName, int number,
            String commitSha, String path, String diff, List<ReviewComment> comments,
            int initialLine, int highlightStartLine, int highlightEndLine, boolean highlightIsRight,
            IntentUtils.InitialCommentMarker initialComment) {
        Intent intent = new Intent(context, PullRequestDiffViewerActivity.class)
                .putExtra("number", number);
        return DiffViewerActivity.fillInIntent(intent, repoOwner, repoName, commitSha, path,
                diff, comments, initialLine, highlightStartLine, highlightEndLine,
                highlightIsRight, initialComment);
    }

    private int mPullRequestNumber;

    @Override
    protected void openCommentDialog(long id, long replyToId, String line, int position,
            int leftLine, int rightLine, PositionalCommentBase commitComment) {
        String body = commitComment == null ? "" : commitComment.body();
        Intent intent = EditPullRequestDiffCommentActivity.makeIntent(this,
                mRepoOwner, mRepoName, mSha, mPath, line, leftLine, rightLine,
                position, id, body, mPullRequestNumber, replyToId);
        startActivityForResult(intent, REQUEST_EDIT);
    }

    @Override
    protected void onInitExtras(Bundle extras) {
        super.onInitExtras(extras);
        mPullRequestNumber = extras.getInt("number", -1);
    }

    @Override
    protected Loader<LoaderResult<List<ReviewComment>>> createCommentLoader() {
        return new PullRequestCommentsLoader(this, mRepoOwner, mRepoName, mPullRequestNumber);
    }

    @Override
    protected String createUrl(String lineId, long replyId) {
        String link = "https://github.com/" + mRepoOwner + "/" + mRepoName + "/pull/"
                + mPullRequestNumber + "/files";
        if (replyId > 0L) {
            link += "#r" + replyId;
        } else {
            link += "#diff-" + ApiHelpers.md5(mPath) + lineId;
        }
        return link;
    }

    @Override
    protected Intent navigateUp() {
        return PullRequestActivity.makeIntent(this, mRepoOwner, mRepoName, mPullRequestNumber);
    }

    @Override
    protected boolean canReply() {
        return true;
    }

    @Override
    protected PositionalCommentBase onUpdateReactions(PositionalCommentBase comment,
            Reactions reactions) {
        return ((ReviewComment) comment).toBuilder()
                .reactions(reactions)
                .build();
    }

    @Override
    protected void deleteComment(long id) throws IOException {
        PullRequestReviewCommentService service =
                Gh4Application.get().getGitHubService(PullRequestReviewCommentService.class);
        ApiHelpers.throwOnFailure(service.deleteComment(mRepoOwner, mRepoName, id).blockingGet());
    }

    @Override
    public List<Reaction> loadReactionDetailsInBackground(ReactionBar.Item item) throws IOException {
        final CommitCommentWrapper comment = (CommitCommentWrapper) item;
        final ReactionService service = Gh4Application.get().getGitHubService(ReactionService.class);

        return ApiHelpers.Pager.fetchAllPages(new ApiHelpers.Pager.PageProvider<Reaction>() {
            @Override
            public Page<Reaction> providePage(long page) throws IOException {
                return ApiHelpers.throwOnFailure(service.getPullRequestReviewCommentReactions(
                        mRepoOwner, mRepoName, comment.comment.id(), page).blockingGet());
            }
        });
    }

    @Override
    public Reaction addReactionInBackground(ReactionBar.Item item, String content) throws IOException {
        CommitCommentWrapper comment = (CommitCommentWrapper) item;
        ReactionService service = Gh4Application.get().getGitHubService(ReactionService.class);

        return ApiHelpers.throwOnFailure(service.createPullRequestReviewCommentReaction(
                mRepoOwner, mRepoName, comment.comment.id(),
                ReactionRequest.builder().content(content).build()).blockingGet());
    }
}
