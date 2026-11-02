package com.dcb.controller;

import com.dcb.model.*;
import com.dcb.service.CardService;
import com.dcb.service.NotificationService;
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
    private final NotificationService notifier;
    private final Util util;

    @PostMapping("/apply")
    public ResponseEntity<APIResponse<?>> apply(@AuthenticationPrincipal UserDetails user, @RequestBody @Valid CardApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(util.getSuccessResponse(notifyCard(user, cardService.apply(user.getUsername(), request), "Card application submitted",
                        "We received your card application. The bank will review it shortly."), "Card application submitted."));
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

    @GetMapping("/{cardId}/number")
    public ResponseEntity<APIResponse<?>> number(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.revealNumber(user.getUsername(), cardId), "Card number fetched."));
    }

    @PostMapping("/{cardId}/otp")
    public ResponseEntity<APIResponse<?>> otp(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId) {
        cardService.sendPaymentOtp(user.getUsername(), cardId);
        return ResponseEntity.ok(util.getSuccessResponse(null, "A 6 digit OTP was sent to your registered email."));
    }

    @PutMapping("/{cardId}/pin")
    public ResponseEntity<APIResponse<?>> pin(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId,
                                              @RequestBody @Valid PinChangeRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(notifyCard(user, cardService.changePin(user.getUsername(), cardId, request), "Card PIN changed",
                "The PIN of your card was set or changed."), "Card PIN updated."));
    }

    @PutMapping("/{cardId}/block")
    public ResponseEntity<APIResponse<?>> block(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId) {
        return ResponseEntity.ok(util.getSuccessResponse(notifyCard(user, cardService.block(user.getUsername(), cardId), "Card blocked",
                "Your card was blocked. It can't be used until you unblock it."), "Card blocked."));
    }

    @PutMapping("/{cardId}/unblock")
    public ResponseEntity<APIResponse<?>> unblock(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId) {
        return ResponseEntity.ok(util.getSuccessResponse(notifyCard(user, cardService.unblock(user.getUsername(), cardId), "Card unblocked",
                "Your card was unblocked and can be used again."), "Card unblocked."));
    }

    @PostMapping("/{cardId}/replace")
    public ResponseEntity<APIResponse<?>> replace(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(util.getSuccessResponse(notifyCard(user, cardService.requestReplacement(user.getUsername(), cardId), "Replacement card requested",
                        "Your current card was blocked and a replacement was requested."), "Replacement card requested, the current card is blocked."));
    }

    @PostMapping("/{cardId}/simulate-transaction")
    public ResponseEntity<APIResponse<?>> simulate(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId,
                                                   @RequestHeader("Idempotency-Key") String key,
                                                   @RequestBody @Valid CardSimulationRequest request) {
        var response = cardService.simulate(user.getUsername(), cardId, request, key);
        if (!Boolean.TRUE.equals(response.getReplayed())) {
            notifyTxn(user, response, "Card payment successful", "A payment was made with your " + response.getCardType().toLowerCase() + " card.");
        }
        return ResponseEntity.status(Boolean.TRUE.equals(response.getReplayed()) ? HttpStatus.OK : HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "Card transaction approved (simulated)."));
    }

    @PostMapping("/{cardId}/pay-dues")
    public ResponseEntity<APIResponse<?>> payDues(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId,
                                                  @RequestHeader("Idempotency-Key") String key,
                                                  @RequestBody @Valid CardRepaymentRequest request) {
        var response = cardService.payDues(user.getUsername(), cardId, request, key);
        if (!Boolean.TRUE.equals(response.getReplayed())) {
            notifyTxn(user, response, "Credit card dues paid", "Your credit card dues were paid from your savings account.");
        }
        return ResponseEntity.ok(util.getSuccessResponse(response, "Card dues paid (simulated)."));
    }

    @GetMapping("/{cardId}/transactions")
    public ResponseEntity<APIResponse<?>> transactions(@AuthenticationPrincipal UserDetails user, @PathVariable Long cardId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.transactions(user.getUsername(), cardId, page, size), "Card transactions fetched."));
    }

    private CardResponse notifyCard(UserDetails user, CardResponse card, String subject, String headline) {
        notifier.notify(user.getUsername(), subject, headline, NotificationService.details()
                .add("Card", card.getCardScheme() + " " + words(card.getCardType()) + " card" + (card.getMaskedNumber() == null ? "" : " " + card.getMaskedNumber()))
                .add("Status", card.getStatus()));
        return card;
    }

    private void notifyTxn(UserDetails user, CardTransactionResponse txn, String subject, String headline) {
        notifier.notify(user.getUsername(), subject, headline, NotificationService.details()
                .add("Card", txn.getMaskedNumber())
                .amount("Amount", txn.getAmount())
                .add("Merchant", txn.getMerchantName())
                .add("Reference", txn.getReference())
                .amount("Available credit", txn.getAvailableAfter()));
    }

    private static String words(String value) {
        return value == null ? "" : value.charAt(0) + value.substring(1).toLowerCase();
    }
}
