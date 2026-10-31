package com.dcb.controller;

import com.dcb.model.*;
import com.dcb.service.CardService;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final Util util;

    @PostMapping("/apply")
    public ResponseEntity<APIResponse<?>> apply(@AuthenticationPrincipal UserDetails user, @RequestBody @Valid CardApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(util.getSuccessResponse(cardService.apply(user.getUsername(), request), "Card application submitted."));
    }

    @GetMapping
    public ResponseEntity<APIResponse<?>> list(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.list(user.getUsername()), "Cards fetched."));
    }

    @GetMapping("/{cardId}")
    public ResponseEntity<APIResponse<?>> get(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.get(user.getUsername(), cardId), "Card fetched."));
    }

    @GetMapping("/{cardId}/limit")
    public ResponseEntity<APIResponse<?>> limit(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.limit(user.getUsername(), cardId), "Card limit fetched."));
    }

    @PutMapping("/{cardId}/pin")
    public ResponseEntity<APIResponse<?>> pin(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId,
                                              @RequestBody @Valid PinChangeRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.changePin(user.getUsername(), cardId, request), "Card PIN updated."));
    }

    @PutMapping("/{cardId}/block")
    public ResponseEntity<APIResponse<?>> block(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.block(user.getUsername(), cardId), "Card blocked."));
    }

    @PutMapping("/{cardId}/unblock")
    public ResponseEntity<APIResponse<?>> unblock(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.unblock(user.getUsername(), cardId), "Card unblocked."));
    }

    @PostMapping("/{cardId}/replace")
    public ResponseEntity<APIResponse<?>> replace(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(util.getSuccessResponse(cardService.requestReplacement(user.getUsername(), cardId), "Replacement card requested, the current card is blocked."));
    }

    @PostMapping("/{cardId}/simulate-transaction")
    public ResponseEntity<APIResponse<?>> simulate(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId,
                                                   @RequestHeader("Idempotency-Key") String key,
                                                   @RequestBody @Valid CardSimulationRequest request) {
        var response = cardService.simulate(user.getUsername(), cardId, request, key);
        return ResponseEntity.status(Boolean.TRUE.equals(response.getReplayed()) ? HttpStatus.OK : HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "Card transaction approved (simulated)."));
    }

    @PostMapping("/{cardId}/pay-dues")
    public ResponseEntity<APIResponse<?>> payDues(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId,
                                                  @RequestHeader("Idempotency-Key") String key,
                                                  @RequestBody @Valid CardRepaymentRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.payDues(user.getUsername(), cardId, request, key), "Card dues paid (simulated)."));
    }

    @GetMapping("/{cardId}/transactions")
    public ResponseEntity<APIResponse<?>> transactions(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.transactions(user.getUsername(), cardId, page, size), "Card transactions fetched."));
    }
}
