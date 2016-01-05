/*************************************************************************
 * PLEASE SEE THE FILE "license.txt" (INCLUDED WITH THIS SOFTWARE PACKAGE)
 * FOR LICENSE AND COPYRIGHT INFORMATION.
 *************************************************************************/

/* ---------------------------------------------------------------------
                     Preference Management Routines

   Make_preference() creates a new preference structure of the given type
   with the given id/attribute/value/referent.  (Referent is only used
   for binary preferences.)  The preference is not yet added to preference
   memory, however.

   Preference_add_ref() and preference_remove_ref() are macros for
   incrementing and decrementing the reference count on a preference.

   Possibly_deallocate_preference_and_clones() checks whether a given
   preference and all its clones have reference_count 0, and deallocates
   them all if they do; it returns true if they were actually deallocated,
   false otherwise.   Deallocate_preference() deallocates a given
   preference.  These routines should normally be invoked only via the
   preference_remove_ref() macro.

   Add_preference_to_tm() adds a given preference to preference memory (and
   hence temporary memory).  Remove_preference_from_tm() removes a given
   preference from PM and TM.

   Process_o_rejects_and_deallocate_them() handles the processing of
   o-supported reject preferences.  This routine is called from the firer
   and passed a list of all the o-rejects generated in the current
   preference phase (the list is linked via the "next" fields on the
   preference structures).  This routine removes all preferences for
   matching values from TM, and deallocates the o-reject preferences when
   done.
--------------------------------------------------------------------- */

#ifndef PREFMEM_H
#define PREFMEM_H

#include "kernel.h"
#include "working_memory_activation.h"

typedef unsigned char byte;
typedef struct agent_struct agent;
typedef struct preference_struct preference;
typedef struct symbol_struct Symbol;
typedef char* rhs_value;

#ifdef USE_MEM_POOL_ALLOCATORS
typedef std::list< preference*, soar_module::soar_memory_pool_allocator< preference* > > pref_buffer_list;
#else
typedef std::list< preference* > pref_buffer_list;
#endif

/* ------------------------------------------------------------------------
                               Preferences

   Fields in a preference:

      type:  indicates the type of the preference.  This is one of the
             types defined below:  ACCEPTABLE_PREFERENCE_TYPE, etc.

      o_supported:  true iff the preference has o-support

      in_tm:  true iff the preference is currently in temporary memory

      on_goal_list:  true iff the preference is on the list of preferences
                     supported by its match goal (see all_of_goal_next below)

      reference_count:  (see below)

      id, attr, value, referent:  points to the symbols.  Referent is only
                                  used for binary preferences.

      slot:  points to the slot this preference is for.  (NIL if the
        preference is not in TM.)

      next, prev:  used for a doubly-linked list of preferences of the
                   same type in that particular slot

      all_of_slot_next, all_of_slot_prev:  used for a doubly-linked list
          of all preferences (of any type) in that particular slot

      all_of_goal_next, all_of_goal_prev:  used for a doubly-linked list
          of all preferences supported by this particular match goal.
          This is needed in order to remove all o-support from a particular
          goal when that goal is removed from the context stack.

      next_clone, prev_clone:  used for a doubly-linked list of all "clones"
        of this preference.  When a result is returned from a subgoal and a
        chunk is built, we get two copies of the "same" preference, one from
        the subgoal's production firing, and one from the chunk instantiation.
        If results are returned more than one level, or the same result is
        returned simultaneously by multiple production firings, we can get
        lots of copies of the "same" preference.  These clone preferences
        are kept on a list so that we can find the right one to backtrace
        through, given a wme supported by "all the clones."

      inst:  points to the instantiation that generated this preference

      inst_next, inst_prev:  used for a doubly-linked list of all
        existing preferences that were generated by that instantiation

      next_candidate:  used by the decider for lists of candidate values
        for a certain slot

      next_result:  used by the chunker for a list of result preferences

   Reference counts on preferences:
      +1 if the preference is currently in TM
      +1 for each instantiation condition that points to it (bt.trace)
      +1 if it supports an installed context WME

   We deallocate a preference if:
      (1) reference_count==0 and all its clones have reference_count==0
          (hence it couldn't possibly be needed anymore)
   or (2) its match goal is removed from the context stack
          (hence there's no way we'll ever want to BT through it)
------------------------------------------------------------------------ */


extern const char* preference_name[NUM_PREFERENCE_TYPES];

typedef struct preference_struct
{
    byte type;         /* acceptable, better, etc. */
    bool o_supported;  /* is the preference o-supported? */
    bool in_tm;        /* is this currently in TM? */
    bool on_goal_list; /* is this pref on the list for its match goal */
    uint64_t reference_count;
    Symbol* id;
    Symbol* attr;
    Symbol* value;
    Symbol* referent;

    /* -- These pointers store the identities of the original symbols
     *    associated with a preference generated by a rhs action.  It is
     *    used by the chunker to match up rhs symbols with the correct
     *    lhs one, since the instantiated value is not sufficient. -- */

    soar_module::identity_triple o_ids;
    soar_module::rhs_triple rhs_funcs;

    struct slot_struct* slot;

    /* dll of pref's of same type in same slot */
    struct preference_struct* next, *prev;

    /* dll of all pref's in same slot */
    struct preference_struct* all_of_slot_next, *all_of_slot_prev;

    /* dll of all pref's from the same match goal */
    struct preference_struct* all_of_goal_next, *all_of_goal_prev;

    /* dll (without header) of cloned preferences (created when chunking) */
    struct preference_struct* next_clone, *prev_clone;

    struct instantiation_struct* inst;
    struct preference_struct* inst_next, *inst_prev;
    struct preference_struct* next_candidate;
    struct preference_struct* next_result;

    unsigned int total_preferences_for_candidate;
    double numeric_value;
    bool rl_contribution;
    double rl_rho;                // ratio of target policy to behavior policy

    wma_pooled_wme_set* wma_o_set;

} preference;

extern preference* make_preference(agent* thisAgent, byte type, Symbol* id, Symbol* attr, Symbol* value, Symbol* referent,
                                   const soar_module::identity_triple o_ids = soar_module::identity_triple(0, 0, 0),
                                   const soar_module::rhs_triple rhs_funcs = soar_module::rhs_triple(NULL, NULL, NULL));
extern preference* shallow_copy_preference(agent* thisAgent, preference* pPref);
extern bool possibly_deallocate_preference_and_clones(agent* thisAgent, preference* pref);
inline void preference_add_ref(preference* p) { (p)->reference_count++;}
inline void preference_remove_ref(agent* thisAgent, preference* p) {(p)->reference_count--; if ((p)->reference_count == 0) { possibly_deallocate_preference_and_clones(thisAgent, p);}}
extern void deallocate_preference(agent* thisAgent, preference* pref);
extern bool add_preference_to_tm(agent* thisAgent, preference* pref);
extern void remove_preference_from_tm(agent* thisAgent, preference* pref);
extern bool remove_preference_from_clones(agent* thisAgent, preference* pref);
extern void process_o_rejects_and_deallocate_them(agent* thisAgent, preference* o_rejects, pref_buffer_list& bufdeallo);

#endif
