# Customer Policy

- Policy ID: POL-CUS
- Version: 1.0
- Effective date: 2026-01-01
- Currency: Jordanian Dinar (JOD), amounts with 2 decimal places.
- Scope: This policy classifies customers using only their order statistics and sets how customers are identified in reports.

## Customer statistics

**CUS-1** Customers are classified using only three statistics: orderCount (the number of orders the customer has placed), totalSpent (the total value of the customer's orders, in JOD) and averageOrderValue (the average value of the customer's orders, in JOD). All three statistics cover every order the customer has placed, with no date limit. No other customer attribute is used to classify a customer.

## Customer classes

**CUS-2** A customer is a KEY_ACCOUNT when orderCount >= 3 and totalSpent >= JOD 500.00. Both conditions must be met; a customer with fewer than 3 orders is never a KEY_ACCOUNT, whatever their totalSpent.

**CUS-3** A customer is in the REPEAT class when orderCount >= 2 and the customer does not have both orderCount >= 3 and totalSpent >= JOD 500.00. The term "repeat customer" means any customer with orderCount >= 2, so every customer in the REPEAT class and every KEY_ACCOUNT customer (orderCount >= 3 and totalSpent >= JOD 500.00) is a repeat customer.

**CUS-4** A customer is ONE_TIME when orderCount = 1, whatever their totalSpent.

**CUS-5** A customer is NO_ORDERS when orderCount = 0.

## Average order value of a customer

**CUS-6** A customer has a high average order value when orderCount >= 2 and averageOrderValue >= JOD 200.00. A customer with orderCount below 2 never has a high average order value. The high average order value flag is separate from the customer class: a customer of any class with orderCount >= 2 can have it.

**CUS-7** A customer's averageOrderValue is compared only against the JOD 200.00 threshold for a high average order value (orderCount >= 2 and averageOrderValue >= JOD 200.00). No other averageOrderValue level is defined as high or low for a customer.

## Use of customer classes

**CUS-8** The customer class (KEY_ACCOUNT, REPEAT, ONE_TIME or NO_ORDERS) and the high average order value flag (orderCount >= 2 and averageOrderValue >= JOD 200.00) are used only for reporting, and they do not change any price, discount, return condition or refund.

**CUS-9** KEY_ACCOUNT, REPEAT, ONE_TIME and NO_ORDERS are the only customer classes. No VIP, loyalty, churn-risk or other customer status is defined, so no customer holds such a status.

## Customer identification in reports

**CUS-10** Customer reports and answers identify a customer by customer ID and name only and never include the customer's email address.
