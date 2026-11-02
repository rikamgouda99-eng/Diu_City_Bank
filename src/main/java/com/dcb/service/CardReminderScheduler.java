package com.dcb.service;

import com.dcb.config.BankProperties;
import com.dcb.entity.CardEntity;
import com.dcb.enums.CardStatus;
import com.dcb.model.CardBillingResponse;
import com.dcb.repository.CardRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumSet;

/**
 * Daily job that mails credit card holders with outstanding dues: once when the statement is
 * generated, a few days before the payment due date, and on the due date itself.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CardReminderScheduler {

    private final CardRepo cardRepo;
    private final CardService cardService;
    private final NotificationService notifier;
    private final BankProperties props;

    @Scheduled(cron = "${bank.card.reminder-cron:0 0 9 * * *}")
    public void sendReminders() {
        int sent = sendReminders(LocalDate.now());
        log.info("Card due date reminders sent: {}", sent);
    }

    public int sendReminders(LocalDate today) {
        int sent = 0;
        int before = props.getCard().getReminderDaysBefore();
        for (CardEntity card : cardRepo.findCreditCardsWithDues(EnumSet.of(CardStatus.ACTIVE, CardStatus.BLOCKED))) {
            try {
                CardBillingResponse bill = cardService.billing(card, today);
                String subject;
                String headline;
                if (today.equals(bill.getStatementDate())) {
                    subject = "Credit card statement generated";
                    headline = "Your credit card statement is ready. Please pay before the due date to avoid late charges.";
                } else if (bill.getDaysToDue() == before) {
                    subject = "Credit card payment due in " + before + " days";
                    headline = "This is a reminder that your credit card payment is due soon.";
                } else if (bill.getDaysToDue() == 0) {
                    subject = "Credit card payment due today";
                    headline = "Your credit card payment is due today. Please pay at least the minimum due to avoid late charges.";
                } else {
                    continue;
                }
                notifier.notify(card.getBankId(), subject, headline, NotificationService.details()
                        .add("Card", card.getCardScheme() + " " + card.getMaskedNumber())
                        .amount("Total outstanding", bill.getTotalOutstanding())
                        .amount("Minimum due", bill.getMinimumDue())
                        .add("Statement date", bill.getStatementDate())
                        .add("Payment due date", bill.getPaymentDueDate()));
                sent++;
            } catch (Exception e) {
                log.warn("Could not send due reminder for card {}: {}", card.getCardId(), e.getMessage());
            }
        }
        return sent;
    }
}
