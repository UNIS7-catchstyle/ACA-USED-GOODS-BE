package com.example.app.domain.comment.service;

import com.example.app.domain.comment.dto.CommentNode;
import com.example.app.domain.comment.entity.Comment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Builds the comment tree entirely in memory from a flat, id-ASC-ordered list —
// no further queries regardless of depth or comment count. Shared by the standalone
// comments endpoint and MarketService.getDetail().
@Slf4j
@Component
public class CommentTreeAssembler {

	public List<CommentNode> assemble(List<Comment> comments) {
		if (comments.isEmpty()) {
			return List.of();
		}

		Set<Long> idsInThisMarket = comments.stream().map(Comment::getId).collect(Collectors.toSet());
		Map<Long, List<Comment>> childrenByParentId = new LinkedHashMap<>();
		List<Comment> roots = new ArrayList<>();

		for (Comment comment : comments) {
			Comment parent = comment.getParent();
			if (parent == null) {
				roots.add(comment);
				continue;
			}
			// .getId() on an uninitialized lazy proxy reads the FK column value
			// directly — no extra query just to check whether the parent exists.
			Long parentId = parent.getId();
			if (!idsInThisMarket.contains(parentId)) {
				// Shouldn't happen given POST's parentId validation, but a comment
				// whose parent belongs to a different market (or is otherwise
				// missing) is dropped rather than promoted to a fake root.
				log.warn("Discarding orphan comment: id={}, missing/foreign parentId={}", comment.getId(), parentId);
				continue;
			}
			childrenByParentId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(comment);
		}

		return roots.stream().map(root -> toNode(root, childrenByParentId)).toList();
	}

	private CommentNode toNode(Comment comment, Map<Long, List<Comment>> childrenByParentId) {
		List<CommentNode> children = childrenByParentId.getOrDefault(comment.getId(), List.of()).stream()
				.map(child -> toNode(child, childrenByParentId))
				.toList();
		return new CommentNode(
				comment.getId(),
				new CommentNode.Author(comment.getUser().getId(), comment.getUser().getNickname()),
				comment.getContent(),
				comment.getImageUrl(),
				comment.getCreatedAt(),
				children);
	}
}
