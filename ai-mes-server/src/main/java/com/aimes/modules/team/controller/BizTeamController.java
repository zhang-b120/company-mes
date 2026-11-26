package com.aimes.modules.team.controller;

import com.aimes.common.result.R;
import com.aimes.modules.team.entity.BizTeam;
import com.aimes.modules.team.service.BizTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/team/teams")
@RequiredArgsConstructor
public class BizTeamController {

    private final BizTeamService teamService;

    @GetMapping
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) String workshop,
            @RequestParam(required = false) String shiftType,
            @RequestParam(required = false) String keyword) {
        return R.ok(teamService.list(workshop, shiftType, keyword));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizTeam team) {
        teamService.create(team);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizTeam team) {
        teamService.update(id, team);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        teamService.delete(id);
        return R.ok();
    }

    @GetMapping("/{teamId}/members")
    public R<List<Map<String, Object>>> getMembers(@PathVariable Long teamId) {
        return R.ok(teamService.getMembers(teamId));
    }

    @PostMapping("/{teamId}/members")
    public R<Void> addMembers(@PathVariable Long teamId, @RequestBody Map<String, List<Long>> body) {
        teamService.addMembers(teamId, body.get("userIds"));
        return R.ok();
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    public R<Void> removeMember(@PathVariable Long teamId, @PathVariable Long memberId) {
        teamService.removeMember(teamId, memberId);
        return R.ok();
    }

    @PutMapping("/{teamId}/members/weights")
    public R<Void> saveWeights(@PathVariable Long teamId, @RequestBody List<Map<String, Object>> weightList) {
        teamService.saveWeights(teamId, weightList);
        return R.ok();
    }

    @PutMapping("/{teamId}/members/weights/equalize")
    public R<Void> equalizeWeights(@PathVariable Long teamId) {
        teamService.equalizeWeights(teamId);
        return R.ok();
    }
}
