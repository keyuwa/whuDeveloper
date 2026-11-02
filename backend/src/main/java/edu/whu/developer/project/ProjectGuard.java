package edu.whu.developer.project;

import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProjectGuard {

    private final ProjectRepository projects;

    public ProjectGuard(ProjectRepository projects) {
        this.projects = projects;
    }

    public Project requireVisible(Long projectId) {
        AuthUser user = AuthSupport.current();
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));
        boolean allowed = project.getStudent().getId().equals(user.getId())
                || project.getPublisher().getId().equals(user.getId());
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该项目");
        }
        return project;
    }
}
