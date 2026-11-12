# Project Details — An Interactive Device for Assessing and Training Daily Living Skills

> **Source:** `Project Details(1).pdf` — 12 pages.  
> The content was reformatted into Markdown headings, lists and tables, preserving the project details, the sequence of stages, and the examples given in the source.

## First: The general idea of the project

A standalone interactive device with a large touch screen, designed to help children who have difficulties performing daily living skills, such as dressing and undressing, eating and drinking, and personal hygiene.

The device's function is not limited to showing pictures or videos that teach the child how to perform the skills; it carries out a chain of connected operations:

1. Receive the child's data and determine the skills to be assessed.
2. Present interactive tasks to the child that simulate daily living activities.
3. Assess the child's performance in each step of the skill.
4. Analyse the difficulties and link them to the related functional domains.
5. Identify the sub-skills that need development.
6. Generate a proposed individual occupational therapy intervention plan.
7. Provide graded training activities according to the child's level.
8. Follow up the child's progress and update the plan based on the results of later assessments.

**The core idea:** Instead of only knowing that the child cannot put on the jacket, we want to know: at which step does the child face difficulty? Which functional skills may be related to this difficulty? And which activities can help the child develop them?

## Second: Target group

Children with autism spectrum disorder.

## Third: The areas covered by the device

The device contains three main self-care sections:

### 1. Dressing and undressing skill

- Jacket.
- T-shirt.
- Shirt.
- Trousers.
- Socks.
- Shoes.

### 2. Eating and drinking skill

**Eating:**

- Sandwich.
- Spoon.
- Fork.

**Drinking:**

- Cup.
- Bottle.
- Straw.

### 3. Personal hygiene skill

- Handwashing.
- Toothbrushing.

## Fourth: The functional domains the device relies on in its analysis

Before explaining operation, the analysis base that the AI relies on must be defined.

The device does not assess the dressing skill as a single skill only; it breaks it down into steps, then links each step to the functional domains it requires.

| No. | Functional domain | Functional sub-skills |
|---:|---|---|
| 1 | Motor planning | Identifying the starting point, identifying the direction, organising the movement, sequencing the steps. |
| 2 | Hand skills | Reach, grasp, carry, release. |
| 3 | Motor coordination | Unilateral, bilateral, simultaneous, reciprocal, visual-motor, fine, and gross coordination. |
| 4 | Range of motion | Movement of the shoulder, elbow, wrist, fingers, hip, knee, and ankle. |
| 5 | Midline crossing | Moving the hand across the middle of the body to reach the opposite side. |

There must be a database that links each step to its appropriate functional requirements, because a single step may require more than one domain.

### Example: Grasping the jacket zipper

| Domain | Functional requirement linked to the step |
|---|---|
| Hand skills | Precision grasp. |
| Motor coordination | Visual-motor coordination. |
| Motor planning | Identifying the grasping position and organising the start of the movement. |
| Range of motion | Reaching the zipper position, if that requires additional movement. |

## Fifth: The child's journey inside the device from start to finish

### Stage 1: Turning on the device and the home screen appearing

When the device is turned on, a welcome screen appears containing:

- The device logo.
- A "Start" button.
- A "Next" button.
- A "Help" button.
- A "Repeat" button.

The interface is designed with calm colours and clear drawings, with optional audio instructions suited to children who need visual or auditory support.

### Stage 2: Recording the child's data and creating their file

A screen for the therapist or the person responsible for the device appears, and a separate file is created for each child.

The file includes:

- The child's name.
- The child's code or identification number.
- Chronological age.

### Stage 3: Choosing the daily living activity to be assessed

Three choices appear in front of the child:

1. Dressing and undressing.
2. Eating and drinking.
3. Personal hygiene.

**Example selection path:**

1. The child chooses "Dressing and undressing".
2. They move to the screen of clothes available for assessment: jacket, T-shirt, shirt, trousers, socks, shoes.
3. If they choose "Jacket", two tasks appear:
   - Taking off the jacket.
   - Putting on the jacket.
4. The therapist can choose the skill they want to assess, or assess both skills together.

### Stage 4: Breaking the skill down into small steps

This is one of the device's most important stages. After choosing "Taking off the jacket", the device does not present the task as a single step; it divides it into consecutive steps.

**Example: The skill of taking off the jacket**

1. Locate the zipper or the buttons.
2. Reach the zipper.
3. Grasp the zipper.
4. Open the zipper.
5. Grasp both sides of the jacket.
6. Move the jacket away from the body.
7. Take the first arm out of the sleeve.
8. Take the second arm out of the sleeve.
9. Take the jacket off completely.
10. Put the jacket in the designated place.

Each step appears with a clear picture or illustration, accompanied by an audio recording.

**Illustrative example:** The screen shows a picture of a child grasping the jacket zipper, and the phrase "Grasp the zipper" appears; the sentence is also heard as audio from the screen to help the child if they cannot read well.

When the steps are finished, a cartoon character (a boy) appears and performs the steps in order.

### Stage 5: Assessing the child in each step

This is where the functional assessment begins.

The device displays the step and asks the child to perform it using real clothes under the therapist's observation.

**The performance recording mechanism mentioned in the source:**

- The therapist's assessment through a dedicated screen, with the use of the camera.
- The child has **three attempts** to perform the step.
- If the child performs the step on the first attempt, the camera records the response.
- If they do not perform it the first time, they are given a second attempt, then a third attempt if needed.
- If they do not perform the step on the third attempt, the camera records that the step was not performed, and the last attempt is recorded.

**Levels for assessing the child's performance in each step:**

| Performance level | Description | Score |
|---|---|:---:|
| Independent | Performs the step without help. | 3 |
| Partial assistance | Needs help with part of the step. | 2 |
| Full assistance | Needs direct help to complete the step. | 1 |

### Stage 6: Analysing the assessment results using AI

After the assessment of all steps is finished, the data moves to the AI unit.

Here the most important operation in the project takes place: converting the results of the child's performance in daily living activities into a functional analysis that identifies the skills needing intervention.

#### Example: A child's assessment results in the skill of taking off the jacket

| Step | Child's result |
|---|---|
| Locating the zipper | Independent |
| Reaching the zipper | Independent |
| Grasping the zipper | Needs partial assistance |
| Opening the zipper | Needs full assistance |
| Grasping both sides of the jacket | Independent |
| Moving the jacket away from the body | Independent |
| Taking the first arm out | Independent |
| Taking the second arm out | Needs partial assistance |
| Taking the jacket off completely | Independent |
| Putting the jacket in the designated place | Independent |

The device does not stop at writing that the child has difficulty opening the zipper; it analyses the step in which the difficulty appeared.

#### Example analysis of the two steps of grasping the zipper and opening it

**1. Hand skills:**

- Precision grasp.
- Finger control.
- Manipulating small objects.

**2. Motor coordination:**

- Visual-motor coordination.
- Bilateral coordination between the two hands.

**3. Motor planning:**

- Organising the grasping and pulling movement.
- Identifying the pulling direction.

**The device's conclusion:** There is a noticeable difficulty in performing the step of opening the zipper, and it may be related to hand skills, motor coordination and motor planning. These domains need additional functional examination before the cause of the difficulty is determined.

#### How does the device know which domain needs intervention?

The system uses a functional database containing:

1. Each of the daily living skills.
2. The steps for performing the skill.
3. The functional domains required for each step.
4. The functional sub-skills linked to each domain.
5. The performance indicators that can be observed.
6. The therapeutic activities that target these skills.

Then the AI links three things:

1. **The child's performance:** Which step could they not perform?
2. **The functional domains:** Which skill does this step require?
3. **The functional skills:** Which skills need assessment and training?

### Stage 7: Identifying strengths and needs

#### The child's functional report

**Strengths:**

- Locating the zipper.
- Reaching the clothes.
- Grasping both sides of the jacket.
- Taking the arms out of the sleeves with an appropriate degree of independence.

**Needs:**

- Precision grasp.
- Finger control.
- Visual-motor coordination.
- Bilateral coordination between the two hands.
- Organising the zipper-opening movement.

**Functional result:** The child needs an intervention that targets improving the hand skills and motor coordination related to opening the zipper, while maintaining the skills they perform independently.

### Stage 8: Creating the individual treatment plan

This is the stage that distinguishes the device from traditional educational programmes.

After identifying the skills that need development, the AI creates a proposed individual occupational therapy intervention plan, based on the assessment results and the child's needs.

The plan is not random; it consists of:

1. The functional problem that appeared in the assessment.
2. Strengths and needs.
3. The general goal.
4. The main goals.
5. The sub-goals.
6. The therapeutic activities.
7. The tools used.
8. How the activity is performed.
9. The difficulty level.
10. How improvement is measured.

## Practical example: The treatment plan for a child who has difficulty opening the jacket zipper

### First: The functional problem

The child has difficulty grasping the jacket zipper and controlling its movement to open it independently.

### Second: The targeted functional domains

- Hand skills.
- Motor coordination.
- Motor planning.

### Third: The general goal

That the child becomes able to open and close the jacket zipper with a degree of independence appropriate to their abilities.

### Fourth: Main goals, sub-goals and activities

#### Main goal 1: Developing hand skills

**Sub-goal:** That the child becomes able to use a precision grasp to hold small objects.

**Activities:**

1. Picking up large beads or age-appropriate pieces using the fingers.
2. Holding clothespins and opening and closing them.
3. Picking up suitable, safe small pieces and moving them from one container to another.

#### Main goal 2: Developing motor coordination

**Sub-goal:** That the child becomes able to coordinate the movement of both hands together while performing a functional task.

**Activities:**

1. Holding a piece of fabric steady with one hand and pulling it with the other hand.
2. Opening and closing a large training zipper fixed on a board.
3. Holding the garment steady with one hand and moving the zipper with the other hand.

#### Main goal 3: Developing motor planning

**Sub-goal:** That the child becomes able to carry out the steps of opening the zipper in the correct order.

**Activities:**

1. Arranging pictures of the steps of opening the zipper.
2. Imitating a practical model of opening a training zipper.
3. Performing the zipper opening starting from identifying the grasping position, then holding it steady, then moving it.

### Fifth: Grading the training

| Level | Training method |
|---|---|
| Simple | A large zipper fixed on a training board. |
| Intermediate | A zipper fixed on a garment placed in front of the child. |
| Advanced | Opening the zipper of the jacket the child is wearing. |

The child does not move to a higher level merely because the number of attempts has finished; rather, after achieving a performance criterion set by the therapist.

### Sixth: Assessment after the intervention

The device re-assesses the child in the step of opening the zipper and compares the results with the previous assessment:

- Do they now grasp the zipper independently?
- Do they now coordinate between the two hands?
- Can they carry out the steps in order?
- Has the need for assistance decreased?

If the child improves, the training moves gradually to a harder level. If they do not improve, the therapist reviews the plan and modifies the activities, or re-assesses the domain causing the difficulty.
