/*
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

package pl.net.was.rest.github.function;

import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;
import io.trino.spi.PageBuilder;
import io.trino.spi.block.Block;
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlType;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import pl.net.was.rest.Rest;
import pl.net.was.rest.github.GithubTable;
import pl.net.was.rest.github.model.IssueComment;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

import static io.trino.spi.type.StandardTypes.INTEGER;
import static io.trino.spi.type.StandardTypes.VARCHAR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static pl.net.was.rest.github.GithubRest.ISSUE_COMMENTS_TABLE_TYPE;
import static pl.net.was.rest.github.GithubRest.getRowType;

@ScalarFunction(value = "issue_comments", deterministic = false)
@Description("Get issue comments")
public class IssueComments
        extends BaseFunction
{
    public IssueComments()
    {
        RowType rowType = getRowType(GithubTable.ISSUE_COMMENTS);
        arrayType = new ArrayType(rowType);
        pageBuilder = new PageBuilder(ImmutableList.of(arrayType));
    }

    @SqlType(ISSUE_COMMENTS_TABLE_TYPE)
    public Block getPage(
            @SqlType(VARCHAR) Slice owner,
            @SqlType(VARCHAR) Slice repo,
            @SqlType(INTEGER) long page,
            @SqlType("timestamp(3)") long since)
            throws IOException
    {
        Response<List<IssueComment>> response = service.listIssueComments(
                "Bearer " + token,
                owner.toStringUtf8(),
                repo.toStringUtf8(),
                PER_PAGE,
                (int) page,
                "updated",
                "asc",
                ISO_LOCAL_DATE_TIME.format(fromTrinoTimestamp(since)) + "Z").execute();
        if (response.code() == HTTP_NOT_FOUND) {
            return null;
        }
        Rest.checkServiceResponse(response);
        List<IssueComment> items = requireNonNull(response.body(), "response body is null");
        items.forEach(i -> i.setOwner(owner.toStringUtf8()));
        items.forEach(i -> i.setRepo(repo.toStringUtf8()));
        return buildBlock(items);
    }
}
