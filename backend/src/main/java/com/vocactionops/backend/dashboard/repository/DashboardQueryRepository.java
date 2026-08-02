package com.vocactionops.backend.dashboard.repository;

import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.Priority;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class DashboardQueryRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public DashboardQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public FeedbackMetrics getFeedbackMetrics(
			Long organizationId,
			LocalDateTime fromDate,
			LocalDateTime toDateExclusive
	) {
		StringBuilder sql = new StringBuilder("""
				SELECT COUNT(feedback.id) AS total_feedback_count,
				       COALESCE(SUM(CASE
				           WHEN analysis.status = 'SUCCESS' THEN 1 ELSE 0
				       END), 0) AS analyzed_feedback_count,
				       COALESCE(SUM(CASE
				           WHEN analysis.status = 'SUCCESS' AND analysis.sentiment = 'NEGATIVE'
				           THEN 1 ELSE 0
				       END), 0) AS negative_feedback_count
				FROM feedbacks feedback
				LEFT JOIN feedback_analysis analysis ON analysis.feedback_id = feedback.id
				WHERE feedback.organization_id = :organizationId
				""");
		MapSqlParameterSource parameters = parameters(organizationId);
		addDateRange(
				sql,
				parameters,
				"COALESCE(feedback.feedback_created_at, feedback.ingested_at)",
				fromDate,
				toDateExclusive
		);

		return jdbcTemplate.queryForObject(sql.toString(), parameters, (resultSet, rowNumber) ->
				new FeedbackMetrics(
						resultSet.getLong("total_feedback_count"),
						resultSet.getLong("analyzed_feedback_count"),
						resultSet.getLong("negative_feedback_count")
				)
		);
	}

	public IssueMetrics getIssueMetrics(
			Long organizationId,
			LocalDateTime fromDate,
			LocalDateTime toDateExclusive
	) {
		MapSqlParameterSource parameters = parameters(organizationId);
		String createdDateRange = dateRangeCondition(
				parameters,
				"issue.created_at",
				fromDate,
				toDateExclusive
		);
		String sql = """
				SELECT COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)
				           AS new_issue_count,
				       COALESCE(SUM(CASE
				           WHEN issue.priority = 'P0'
				            AND issue.status NOT IN ('RESOLVED', 'MONITORING', 'CLOSED')
				           THEN 1 ELSE 0
				       END), 0)
				           AS p0_issue_count,
				       COALESCE(SUM(CASE
				           WHEN issue.priority = 'P1'
				            AND issue.status NOT IN ('RESOLVED', 'MONITORING', 'CLOSED')
				           THEN 1 ELSE 0
				       END), 0)
				           AS p1_issue_count,
				       COALESCE(SUM(CASE
				           WHEN issue.status NOT IN ('RESOLVED', 'MONITORING', 'CLOSED')
				           THEN 1 ELSE 0
				       END), 0) AS unresolved_issue_count
				FROM issues issue
				WHERE issue.organization_id = :organizationId
				""".formatted(createdDateRange);

		return jdbcTemplate.queryForObject(sql, parameters, (resultSet, rowNumber) ->
				new IssueMetrics(
						resultSet.getLong("new_issue_count"),
						resultSet.getLong("p0_issue_count"),
						resultSet.getLong("p1_issue_count"),
						resultSet.getLong("unresolved_issue_count")
				)
		);
	}

	public BigDecimal getAverageResolutionHours(
			Long organizationId,
			LocalDateTime fromDate,
			LocalDateTime toDateExclusive
	) {
		StringBuilder sql = new StringBuilder("""
				SELECT AVG(TIMESTAMPDIFF(SECOND, issue.created_at, issue.resolved_at)) / 3600
				       AS average_resolution_hours
				FROM issues issue
				WHERE issue.organization_id = :organizationId
				  AND issue.resolved_at IS NOT NULL
				""");
		MapSqlParameterSource parameters = parameters(organizationId);
		addDateRange(sql, parameters, "issue.resolved_at", fromDate, toDateExclusive);

		return jdbcTemplate.queryForObject(
				sql.toString(),
				parameters,
				(resultSet, rowNumber) -> resultSet.getBigDecimal("average_resolution_hours")
		);
	}

	public List<CategoryFeedbackMetrics> getCategoryFeedbackMetrics(
			Long organizationId,
			LocalDateTime fromDate,
			LocalDateTime toDateExclusive
	) {
		StringBuilder sql = new StringBuilder("""
				SELECT UPPER(TRIM(analysis.category)) AS category,
				       COUNT(feedback.id) AS feedback_count,
				       COALESCE(SUM(CASE
				           WHEN analysis.sentiment = 'NEGATIVE' THEN 1 ELSE 0
				       END), 0) AS negative_feedback_count
				FROM feedback_analysis analysis
				JOIN feedbacks feedback ON feedback.id = analysis.feedback_id
				WHERE feedback.organization_id = :organizationId
				  AND analysis.status = 'SUCCESS'
				""");
		MapSqlParameterSource parameters = parameters(organizationId);
		addDateRange(
				sql,
				parameters,
				"COALESCE(feedback.feedback_created_at, feedback.ingested_at)",
				fromDate,
				toDateExclusive
		);
		sql.append("GROUP BY UPPER(TRIM(analysis.category))\n");

		return jdbcTemplate.query(sql.toString(), parameters, (resultSet, rowNumber) ->
				new CategoryFeedbackMetrics(
						resultSet.getString("category"),
						resultSet.getLong("feedback_count"),
						resultSet.getLong("negative_feedback_count")
				)
		);
	}

	public List<CategoryIssueMetrics> getActiveIssueCountByCategory(Long organizationId) {
		String sql = """
				SELECT UPPER(TRIM(issue.category)) AS category,
				       COUNT(issue.id) AS issue_count
				FROM issues issue
				WHERE issue.organization_id = :organizationId
				  AND issue.status NOT IN ('RESOLVED', 'MONITORING', 'CLOSED')
				GROUP BY UPPER(TRIM(issue.category))
				""";

		return jdbcTemplate.query(sql, parameters(organizationId), (resultSet, rowNumber) ->
				new CategoryIssueMetrics(
						resultSet.getString("category"),
						resultSet.getLong("issue_count")
				)
		);
	}

	public List<TopIssueMetrics> getTopIssuesByPriorityScore(Long organizationId, int limit) {
		return getTopIssues(
				organizationId,
				limit,
				"""
						CASE WHEN issue.priority_score IS NULL THEN 1 ELSE 0 END,
						issue.priority_score DESC,
						feedback_count DESC,
						issue.id
						"""
		);
	}

	public List<TopIssueMetrics> getTopIssuesByFeedbackCount(Long organizationId, int limit) {
		return getTopIssues(
				organizationId,
				limit,
				"""
						feedback_count DESC,
						CASE WHEN issue.priority_score IS NULL THEN 1 ELSE 0 END,
						issue.priority_score DESC,
						issue.id
						"""
		);
	}

	private List<TopIssueMetrics> getTopIssues(
			Long organizationId,
			int limit,
			String orderBy
	) {
		String sql = """
				SELECT issue.id AS issue_id,
				       issue.title,
				       issue.category,
				       issue.priority,
				       issue.priority_score,
				       issue.status,
				       assignee.id AS assignee_id,
				       assignee.name AS assignee_name,
				       issue.last_seen_at,
				       COUNT(DISTINCT link.id) AS feedback_count,
				       COUNT(DISTINCT CASE
				           WHEN analysis.status = 'SUCCESS' THEN analysis.id
				       END) AS analyzed_feedback_count,
				       COUNT(DISTINCT CASE
				           WHEN analysis.status = 'SUCCESS' AND analysis.sentiment = 'NEGATIVE'
				           THEN analysis.id
				       END) AS negative_feedback_count,
				       COUNT(DISTINCT CASE
				           WHEN action_item.status IN ('TODO', 'IN_PROGRESS') THEN action_item.id
				       END) AS unresolved_action_count
				FROM issues issue
				LEFT JOIN users assignee ON assignee.id = issue.assignee_id
				LEFT JOIN issue_feedbacks link ON link.issue_id = issue.id
				LEFT JOIN feedback_analysis analysis ON analysis.feedback_id = link.feedback_id
				LEFT JOIN actions action_item ON action_item.issue_id = issue.id
				WHERE issue.organization_id = :organizationId
				  AND issue.status NOT IN ('RESOLVED', 'MONITORING', 'CLOSED')
				GROUP BY issue.id, issue.title, issue.category, issue.priority,
				         issue.priority_score, issue.status, assignee.id, assignee.name,
				         issue.last_seen_at
				ORDER BY %s
				LIMIT :limit
				""".formatted(orderBy);
		MapSqlParameterSource parameters = parameters(organizationId)
				.addValue("limit", limit);

		return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> {
			long assigneeId = resultSet.getLong("assignee_id");
			boolean assigneeMissing = resultSet.wasNull();
			Timestamp lastSeenAt = resultSet.getTimestamp("last_seen_at");
			return new TopIssueMetrics(
					resultSet.getLong("issue_id"),
					resultSet.getString("title"),
					resultSet.getString("category"),
					Priority.valueOf(resultSet.getString("priority")),
					resultSet.getBigDecimal("priority_score"),
					IssueStatus.valueOf(resultSet.getString("status")),
					resultSet.getLong("feedback_count"),
					resultSet.getLong("analyzed_feedback_count"),
					resultSet.getLong("negative_feedback_count"),
					resultSet.getLong("unresolved_action_count"),
					assigneeMissing ? null : assigneeId,
					resultSet.getString("assignee_name"),
					lastSeenAt == null ? null : lastSeenAt.toLocalDateTime()
			);
		});
	}

	private static MapSqlParameterSource parameters(Long organizationId) {
		return new MapSqlParameterSource("organizationId", organizationId);
	}

	private static void addDateRange(
			StringBuilder sql,
			MapSqlParameterSource parameters,
			String column,
			LocalDateTime fromDate,
			LocalDateTime toDateExclusive
	) {
		if (fromDate != null) {
			sql.append("  AND ").append(column).append(" >= :fromDate\n");
			parameters.addValue("fromDate", fromDate);
		}
		if (toDateExclusive != null) {
			sql.append("  AND ").append(column).append(" < :toDateExclusive\n");
			parameters.addValue("toDateExclusive", toDateExclusive);
		}
	}

	private static String dateRangeCondition(
			MapSqlParameterSource parameters,
			String column,
			LocalDateTime fromDate,
			LocalDateTime toDateExclusive
	) {
		StringBuilder condition = new StringBuilder("1 = 1");
		if (fromDate != null) {
			condition.append(" AND ").append(column).append(" >= :fromDate");
			parameters.addValue("fromDate", fromDate);
		}
		if (toDateExclusive != null) {
			condition.append(" AND ").append(column).append(" < :toDateExclusive");
			parameters.addValue("toDateExclusive", toDateExclusive);
		}
		return condition.toString();
	}

	public record FeedbackMetrics(
			long totalFeedbackCount,
			long analyzedFeedbackCount,
			long negativeFeedbackCount
	) {
	}

	public record IssueMetrics(
			long newIssueCount,
			long p0IssueCount,
			long p1IssueCount,
			long unresolvedIssueCount
	) {
	}

	public record CategoryFeedbackMetrics(
			String category,
			long feedbackCount,
			long negativeFeedbackCount
	) {
	}

	public record CategoryIssueMetrics(String category, long issueCount) {
	}

	public record TopIssueMetrics(
			Long issueId,
			String title,
			String category,
			Priority priority,
			BigDecimal priorityScore,
			IssueStatus status,
			long feedbackCount,
			long analyzedFeedbackCount,
			long negativeFeedbackCount,
			long unresolvedActionCount,
			Long assigneeId,
			String assigneeName,
			LocalDateTime lastSeenAt
	) {
	}
}
